import { provideHttpClient, HttpEventType } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService, errorMessage } from './api';

describe('API contracts', () => {
  let api: ApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('serializes every lead filter, retaining the zero-based first page', () => {
    api.leads({ search: 'Asha & Co', status: 'QUALIFIED', source: 'Paid search', leakType: 'UNASSIGNED', page: 0, size: 20, sort: 'revenue,desc' }).subscribe();
    const request = http.expectOne(req => req.url === '/api/leads');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys().sort()).toEqual(['leakType', 'page', 'search', 'size', 'sort', 'source', 'status']);
    expect(request.request.params.get('search')).toBe('Asha & Co');
    expect(request.request.params.get('source')).toBe('Paid search');
    expect(request.request.params.get('status')).toBe('QUALIFIED');
    expect(request.request.params.get('leakType')).toBe('UNASSIGNED');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.get('sort')).toBe('revenue,desc');
    expect(request.request.urlWithParams).toContain('search=Asha%20%26%20Co');
    request.flush({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('omits blank and unspecified optional query parameters', () => {
    api.leads({ search: '', status: undefined, source: '', page: 2 }).subscribe();
    const request = http.expectOne('/api/leads?page=2');
    expect(request.request.params.keys()).toEqual(['page']);
    request.flush({ items: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('exports every matching lead with filters and sorting, without pagination', () => {
    api.exportLeads({ search: 'Asha & Co', status: 'WON', source: 'Referral', leakType: 'SLOW_RESPONSE', sort: 'name,asc' }).subscribe();
    const request = http.expectOne(req => req.url === '/api/leads/export');
    expect(request.request.responseType).toBe('blob');
    expect(request.request.params.keys().sort()).toEqual(['leakType', 'search', 'sort', 'source', 'status']);
    expect(request.request.params.get('search')).toBe('Asha & Co');
    expect(request.request.params.get('status')).toBe('WON');
    expect(request.request.params.get('source')).toBe('Referral');
    expect(request.request.params.get('leakType')).toBe('SLOW_RESPONSE');
    expect(request.request.params.get('sort')).toBe('name,asc');
    request.flush(new Blob(['lead_id,name'], { type: 'text/csv' }));
  });

  it('sends CSV as multipart form data and exposes upload progress', () => {
    const file = new File(['lead_id,name,status,created_at'], 'leads.csv', { type: 'text/csv' });
    const receivedTypes: number[] = [];
    api.upload(file).subscribe(event => receivedTypes.push(event.type));
    const request = http.expectOne('/api/leads/upload');
    expect(request.request.method).toBe('POST');
    expect(request.request.reportProgress).toBe(true);
    expect((request.request.body as FormData).get('file')).toBe(file);
    request.event({ type: HttpEventType.UploadProgress, loaded: 5, total: 10 });
    request.flush({ total: 1, inserted: 1, updated: 0, failed: 0, errors: [] });
    expect(receivedTypes).toContain(HttpEventType.UploadProgress);
    expect(receivedTypes).toContain(HttpEventType.Response);
  });

  it('requests a paged gap drill-down and a binary CSV export', () => {
    api.leakLeads('NO_SHOW', 2, 10).subscribe();
    const leads = http.expectOne('/api/leaks/NO_SHOW/leads?page=2&size=10');
    leads.flush({ items: [], page: 2, size: 10, totalElements: 0, totalPages: 0 });
    api.exportLeak('NO_SHOW').subscribe();
    const exportRequest = http.expectOne('/api/leaks/NO_SHOW/export');
    expect(exportRequest.request.responseType).toBe('blob');
    exportRequest.flush(new Blob(['\ufefflead_id,name'], { type: 'text/csv' }));
  });

  it('preserves server validation messages and explains an unreachable backend', () => {
    expect(errorMessage({ status: 400, error: { message: 'Missing required header: lead_id' } })).toBe('Missing required header: lead_id');
    expect(errorMessage({ status: 0 })).toContain('Cannot reach the API');
    expect(errorMessage({ status: 502, message: 'Bad Gateway' })).toContain('start-local.ps1');
    expect(errorMessage({ status: 504, message: 'Gateway Timeout' })).toContain('Cannot reach the API');
    expect(errorMessage(null)).toBe('Something went wrong. Please retry.');
  });
});
