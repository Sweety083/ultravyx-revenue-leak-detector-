"""Exercise the running local app using fictional sample records; no data is deleted.

Usage: python scripts/smoke-test.py [--base http://127.0.0.1:4200]
Imports sample-leads.csv twice and updates an existing sample row for validation checks.
"""
import argparse
import csv
import datetime as dt
import io
import json
from collections import Counter
from decimal import Decimal
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

parser = argparse.ArgumentParser()
parser.add_argument('--base', default='http://127.0.0.1:4200')
args = parser.parse_args()
base = args.base.rstrip('/')

def call(path, data=None, content_type=None, status=200, raw=False, origin=None):
    headers = {'Origin': origin or base}
    if content_type:
        headers['Content-Type'] = content_type
    req = Request(base + '/api' + path, data=data, headers=headers)
    try:
        response = urlopen(req, timeout=30)
    except HTTPError as error:
        response = error
    with response:
        body = response.read()
        assert response.status == status, (path, response.status, body.decode(errors='replace'))
        return body if raw else json.loads(body, parse_float=Decimal)

def upload(text, status=200):
    boundary = 'UltravyxSmokeTestBoundary'
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="sample.csv"\r\n'
            f'Content-Type: text/csv\r\n\r\n{text}\r\n--{boundary}--\r\n').encode()
    return call('/leads/upload', body, f'multipart/form-data; boundary={boundary}', status)

def encode_rows(rows):
    stream = io.StringIO(newline='')
    csv.writer(stream).writerows(rows)
    return stream.getvalue()

sample = (Path(__file__).resolve().parents[1] / 'sample-leads.csv').read_text(encoding='utf-8-sig')
sample_rows = list(csv.reader(io.StringIO(sample)))
first = upload(sample)
assert first['total'] == 500 and first['inserted'] + first['updated'] == 500 and first['failed'] == 0, first
second = upload(sample)
assert second == {'total': 500, 'inserted': 0, 'updated': 500, 'failed': 0, 'errors': []}, second
print('PASS: 500-row import and duplicate-free repeat import')

# Keep the valid fixture identical to its original; invalid rows cannot alter the database.
valid = sample_rows[1]
invalid = valid.copy()
invalid[0] = 'SMOKE-INVALID-STATUS'
invalid[2] = 'NOT_A_STATUS'
partial = upload(encode_rows([sample_rows[0], valid, invalid]))
assert partial['updated'] == 1 and partial['failed'] == 1 and partial['errors'][0]['row'] == 3, partial
assert upload('lead_id,name\nX,Example\n', 400)['code'] == 'BAD_REQUEST'
assert upload(encode_rows([sample_rows[0]]), 400)['code'] == 'BAD_REQUEST'
assert upload(encode_rows([sample_rows[0], valid, valid]))['failed'] == 1
print('PASS: partial imports, missing headers, no data rows and duplicate IDs')

all_leads = []
page = 0
while True:
    result = call('/leads?' + urlencode({'page': page, 'size': 100, 'sort': 'name,asc'}))
    all_leads.extend(result['items'])
    if page + 1 >= result['totalPages']:
        break
    page += 1
assert len(all_leads) == result['totalElements'] == len({lead['id'] for lead in all_leads})
by_external = {lead['externalLeadId']: lead for lead in all_leads}
for row in sample_rows[1:]:
    actual = by_external[row[0]]
    assert actual['name'] == row[1] and actual['status'] == row[2]
    assert actual['revenue'] == (Decimal(row[10]) if row[10] else None)
assert call('/leads/' + all_leads[0]['id']) == all_leads[0]
print('PASS: pagination, persisted imported values and lead details')

now = dt.datetime.now(dt.timezone.utc)
def timestamp(value):
    return dt.datetime.fromisoformat(value.replace('Z', '+00:00')) if value else None

def flags(lead):
    found = []
    created, contacted = timestamp(lead['createdAt']), timestamp(lead['contactedAt'])
    if lead['status'] == 'NEW' and contacted is None and now-created >= dt.timedelta(hours=24):
        found.append('UNCONTACTED')
    if lead['status'] not in ['WON','LOST'] and not (lead['assignedTo'] or '').strip():
        found.append('UNASSIGNED')
    if contacted is not None and contacted-created >= dt.timedelta(minutes=60):
        found.append('SLOW_RESPONSE')
    if lead['status'] == 'QUALIFIED' and now-created >= dt.timedelta(days=7):
        found.append('QUALIFIED_NOT_PROGRESSED')
    if lead['status'] == 'NO_SHOW':
        found.append('NO_SHOW')
    assert sorted(found) == sorted(flag['type'] for flag in lead['detectedProblems'])
    return found

expected_flags = {lead['id']: flags(lead) for lead in all_leads}
counts = Counter(flag for found in expected_flags.values() for flag in found)
summary = call('/dashboard/summary')
assert summary['totalLeads'] == len(all_leads)
assert summary['customers'] == sum(lead['status']=='WON' for lead in all_leads)
assert summary['recordedRevenue'] == sum(lead['revenue'] or Decimal(0) for lead in all_leads)
assert summary['affectedLeads'] == sum(bool(found) for found in expected_flags.values())
assert summary['totalFlags'] == sum(counts.values())
assert {entry['type']:entry['count'] for entry in summary['leakCounts']} == dict(counts)
funnel_sets = [None, ['CONTACTED','QUALIFIED','APPOINTMENT','ATTENDED','WON'], ['QUALIFIED','APPOINTMENT','ATTENDED','WON'], ['APPOINTMENT','ATTENDED','WON','NO_SHOW'], ['ATTENDED','WON'], ['WON']]
stages = call('/dashboard/funnel')['stages']
assert [stage['count'] for stage in stages] == [sum(allowed is None or lead['status'] in allowed for lead in all_leads) for allowed in funnel_sets]
assert sum(row['totalLeads'] for row in call('/analytics/sources')) == len(all_leads)
assert sum(row['count'] for row in call('/analytics/response-times')) == len(all_leads)
print('PASS: dashboard revenue, customers, distinct affected leads, flags, funnel and analytics')

for gap in call('/leaks'):
    expected = {lead['externalLeadId'] for lead in all_leads if gap['type'] in expected_flags[lead['id']]}
    affected = call('/leaks/' + gap['type'] + '/leads?size=100')
    assert affected['totalElements'] == gap['count'] == len(expected)
    exported = call('/leaks/' + gap['type'] + '/export', raw=True).decode('utf-8-sig')
    records = list(csv.DictReader(io.StringIO(exported)))
    assert len(records) == len(expected) and {r['lead_id'] for r in records} == expected
print('PASS: all five gap drill-downs and CSV exports')

for params in [{'status':'WON'}, {'source':'Unknown'}, {'leakType':'NO_SHOW'}, {'search':valid[0]}]:
    result = call('/leads?' + urlencode(params | {'size':100}))
    for lead in result['items']:
        if 'status' in params: assert lead['status'] == params['status']
        if 'source' in params: assert (lead['source'] or 'Unknown') == params['source']
        if 'leakType' in params: assert params['leakType'] in expected_flags[lead['id']]
        if 'search' in params: assert params['search'].lower() in lead['externalLeadId'].lower()
for query in ['page=-1','size=0','size=101','sort=invalid,desc','status=INVALID']:
    call('/leads?' + query, status=400)
sorted_leads = call('/leads?sort=revenue,desc&size=100')['items']
assert sorted_leads[0]['revenue'] == max(lead['revenue'] or Decimal(0) for lead in all_leads), 'Highest recorded revenue must sort first, before unknown revenue'
print('PASS: search, status/source/gap filters, invalid queries and revenue sort')

duplicate = {'externalLeadId':valid[0], 'name':'Duplicate', 'status':'NEW', 'createdAt':valid[3]}
call('/leads', json.dumps(duplicate).encode(), 'application/json', 409)
for origin in ['http://127.0.0.1:4200','http://localhost:4200']:
    assert call('/dashboard/summary', origin=origin)['totalLeads'] == len(all_leads)
call('/dashboard/summary', status=403, raw=True, origin='https://unrelated.example')
print('PASS: duplicate manual creation and local-origin restrictions')
print(json.dumps(summary, default=str))
print('All live smoke checks passed.')
