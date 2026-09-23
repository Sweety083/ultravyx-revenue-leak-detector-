import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ToastService } from './toast';
import { UploadComponent } from './upload';

describe('CSV upload', () => {
  let fixture: ComponentFixture<UploadComponent>;
  let http: HttpTestingController;
  let toast: ToastService;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [UploadComponent], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    fixture = TestBed.createComponent(UploadComponent);
    http = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(ToastService);
    await fixture.whenStable();
  });
  afterEach(() => { http.verify(); toast.dismiss(); });

  it('rejects a non-CSV file without contacting the backend', () => {
    fixture.componentInstance.selectFile(new File(['data'], 'leads.xlsx'));
    fixture.componentInstance.upload();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Please select a .csv file.');
    expect(fixture.componentInstance.file).toBeUndefined();
    http.expectNone('/api/leads/upload');
  });

  it('accepts a CSV extension regardless of case and clears an earlier error', () => {
    fixture.componentInstance.selectFile(new File(['data'], 'wrong.txt'));
    const file = new File(['data'], 'leads.CSV', { type: 'text/csv' });
    fixture.componentInstance.selectFile(file);
    expect(fixture.componentInstance.file).toBe(file);
    expect(fixture.componentInstance.error).toBe('');
  });

  it('rejects empty and oversized files before sending a request', () => {
    fixture.componentInstance.selectFile(new File([], 'empty.csv'));
    expect(fixture.componentInstance.error).toContain('empty');
    fixture.componentInstance.selectFile(new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.csv'));
    expect(fixture.componentInstance.error).toContain('10 MB');
    fixture.componentInstance.upload();
    http.expectNone('/api/leads/upload');
  });

  it('keeps the active file fixed while an import is running', () => {
    const original = new File(['data'], 'original.csv');
    fixture.componentInstance.selectFile(original);
    fixture.componentInstance.upload();
    fixture.componentInstance.selectFile(new File(['other data'], 'replacement.csv'));
    expect(fixture.componentInstance.file).toBe(original);
    http.expectOne('/api/leads/upload').flush({ total: 1, inserted: 1, updated: 0, failed: 0, errors: [] });
    fixture.componentInstance.selectFile(new File(['data'], 'wrong.txt'));
    expect(fixture.componentInstance.result).toBeUndefined();
  });

  it('shows progress, retains partial import results, and displays row errors', async () => {
    fixture.componentInstance.selectFile(new File(['data'], 'leads.csv'));
    fixture.componentInstance.upload();
    fixture.componentInstance.upload();
    const request = http.expectOne('/api/leads/upload');
    request.event({ type: HttpEventType.UploadProgress, loaded: 25, total: 100 });
    await fixture.whenStable();
    expect(fixture.componentInstance.progress).toBe(25);
    expect(fixture.nativeElement.textContent).toContain('25%');
    request.flush({ total: 4, inserted: 2, updated: 1, failed: 1, errors: [{ row: 5, field: 'status', message: 'Unknown status: PENDING' }] });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(Array.from(page.querySelectorAll('.result-grid strong')).map(element => element.textContent)).toEqual(['4', '2', '1', '1']);
    expect(page.querySelector('.results tbody')?.textContent).toContain('Unknown status: PENDING');
    expect(page.querySelector('.results tbody')?.textContent).toContain('status');
    expect(page.querySelector('.result-actions a')?.getAttribute('href')).toBe('/dashboard');
    expect(fixture.componentInstance.importing).toBe(false);
    expect(toast.message()).toBe('2 leads added, 1 updated, 1 failed.');
  });

  it('allows a failed import to be retried using the same selected file', async () => {
    const file = new File(['bad data'], 'leads.csv');
    fixture.componentInstance.selectFile(file);
    fixture.componentInstance.upload();
    http.expectOne('/api/leads/upload').flush({ message: 'Missing required header: lead_id' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Missing required header: lead_id');
    expect(fixture.componentInstance.file).toBe(file);
    expect(fixture.componentInstance.importing).toBe(false);
    fixture.componentInstance.upload();
    http.expectOne('/api/leads/upload').flush({ total: 1, inserted: 1, updated: 0, failed: 0, errors: [] });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Import complete');
    expect(fixture.componentInstance.error).toBe('');
    expect(toast.kind()).toBe('success');
  });
});
