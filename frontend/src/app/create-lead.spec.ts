import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { CreateLeadComponent } from './create-lead';

describe('Manual lead creation', () => {
  let fixture: ComponentFixture<CreateLeadComponent>;
  let component: CreateLeadComponent;
  let http: HttpTestingController;
  let dialog: { close: ReturnType<typeof vi.fn>; disableClose: boolean };

  beforeEach(async () => {
    dialog = { close: vi.fn(), disableClose: false };
    await TestBed.configureTestingModule({
      imports: [CreateLeadComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MatDialogRef, useValue: dialog }],
    }).compileComponents();
    fixture = TestBed.createComponent(CreateLeadComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => http.verify());

  function fillRequired(): void {
    component.form.patchValue({ externalLeadId: ' MANUAL-001 ', name: ' Asha Rao ', createdAt: '2026-09-23T09:30' });
  }

  it('shows required field errors and does not submit whitespace-only values', async () => {
    component.form.patchValue({ externalLeadId: '   ', name: '   ', createdAt: '' });
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#error-externalLeadId').textContent).toContain('required');
    expect(fixture.nativeElement.querySelector('#error-name').textContent).toContain('required');
    expect(fixture.nativeElement.querySelector('#error-createdAt').textContent).toContain('required');
    http.expectNone('/api/leads');
  });

  it('submits trimmed text, UTC timestamps and unknown revenue, then returns the saved lead', async () => {
    fillRequired();
    component.form.patchValue({ contactedAt: '2026-09-23T10:30', source: ' Referral ', assignedTo: '  ' });
    component.save();
    component.save();
    await fixture.whenStable();
    expect(dialog.disableClose).toBe(true);
    expect(fixture.nativeElement.querySelector('fieldset').disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    const request = http.expectOne('/api/leads');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      externalLeadId: 'MANUAL-001', name: 'Asha Rao', status: 'NEW',
      createdAt: new Date('2026-09-23T09:30').toISOString(), contactedAt: new Date('2026-09-23T10:30').toISOString(),
      source: 'Referral', assignedTo: null, campaign: null, appointmentAt: null, attendedAt: null, revenue: null,
    });
    const saved = { ...request.request.body, id: 'new-id', detectedProblems: [] };
    request.flush(saved);
    expect(dialog.close).toHaveBeenCalledWith(saved);
  });

  it('preserves explicitly recorded zero revenue', () => {
    fillRequired();
    component.form.patchValue({ revenue: '0.00' });
    component.save();
    const request = http.expectOne('/api/leads');
    expect(request.request.body.revenue).toBe(0);
    request.flush({ id: 'new-id' });
  });

  it.each(['-1', '1.001', '1,000', 'NaN', '99999999999999999'])('rejects invalid or unsafe revenue %s', revenue => {
    fillRequired();
    component.form.patchValue({ revenue });
    component.save();
    expect(component.fieldError('revenue')).not.toBe('');
    http.expectNone('/api/leads');
  });

  it('rejects invalid dates and contact before creation', () => {
    fillRequired();
    component.form.patchValue({ contactedAt: '2026-09-23T08:30', appointmentAt: '2026-02-31T09:30' });
    component.save();
    expect(component.fieldError('contactedAt')).toContain('before lead creation');
    expect(component.fieldError('appointmentAt')).toContain('valid local date');
    http.expectNone('/api/leads');
  });

  it('keeps input after a duplicate ID error and allows a corrected retry', async () => {
    fillRequired();
    component.save();
    http.expectOne('/api/leads').flush({ message: 'External lead ID already exists' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#error-externalLeadId').textContent).toContain('already exists');
    expect(component.form.controls.name.value).toBe(' Asha Rao ');
    expect(dialog.close).not.toHaveBeenCalled();
    expect(dialog.disableClose).toBe(false);
    component.form.controls.externalLeadId.setValue('MANUAL-002');
    expect(component.fieldError('externalLeadId')).toBe('');
    component.save();
    const retry = http.expectOne('/api/leads');
    expect(retry.request.body.externalLeadId).toBe('MANUAL-002');
    retry.flush({ id: 'new-id' });
    expect(dialog.close).toHaveBeenCalled();
  });

  it('displays server field errors and recovers from an unavailable API', async () => {
    fillRequired();
    component.save();
    http.expectOne('/api/leads').flush({ message: 'Please correct the highlighted fields', details: [{ row: 0, field: 'source', message: 'Invalid source' }] }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#error-source').textContent).toBe('Invalid source');
    component.form.controls.source.setValue('Referral');
    component.save();
    http.expectOne('/api/leads').error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Cannot reach the API');
    expect(component.saving).toBe(false);
    expect(dialog.disableClose).toBe(false);
  });
});
