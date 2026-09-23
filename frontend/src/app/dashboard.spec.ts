import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard';
import { Summary } from './api';

describe('Dashboard states', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let http: HttpTestingController;
  const emptySummary: Summary = { totalLeads: 0, customers: 0, recordedRevenue: 0, affectedLeads: 0, totalFlags: 0, leakCounts: [] };
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [DashboardComponent], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    fixture = TestBed.createComponent(DashboardComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => http.verify());

  function respond(summary: Summary): void {
    http.expectOne('/api/dashboard/summary').flush(summary);
    http.expectOne('/api/dashboard/funnel').flush({ stages: [{ name: 'Total leads', count: summary.totalLeads, conversionRate: 100 }] });
    http.expectOne('/api/analytics/sources').flush([]);
    http.expectOne('/api/analytics/response-times').flush([]);
  }

  it('shows a loading skeleton, then a useful upload action for an empty workspace', async () => {
    expect(fixture.nativeElement.querySelector('[aria-label="Loading dashboard"]')).not.toBeNull();
    respond(emptySummary);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('No leads yet');
    expect(page.querySelector('.state-box a')?.getAttribute('href')).toBe('/upload');
    expect(page.querySelector('.kpi')).toBeNull();
  });

  it('renders the server counts separately and links the selected gap', async () => {
    respond({ totalLeads: 12, customers: 2, recordedRevenue: 2500, affectedLeads: 3, totalFlags: 5, leakCounts: [{ type: 'UNCONTACTED', severity: 'HIGH', count: 2 }] });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const kpis = Array.from(page.querySelectorAll('.kpi')).map(card => card.textContent || '');
    expect(kpis.find(card => card.includes('Affected leads'))).toContain('3');
    expect(kpis.find(card => card.includes('Total flags'))).toContain('5');
    expect(kpis.find(card => card.includes('Recorded revenue'))).toContain('2,500');
    expect(page.querySelector('.gap-link')?.getAttribute('href')).toBe('/leaks?type=UNCONTACTED');
    expect(page.textContent).toContain('qualification-entry time is not available');
  });

  it('shows an actionable API error and successfully retries', async () => {
    const summary = http.expectOne('/api/dashboard/summary');
    const funnel = http.expectOne('/api/dashboard/funnel');
    const sources = http.expectOne('/api/analytics/sources');
    const buckets = http.expectOne('/api/analytics/response-times');
    funnel.flush({ stages: [] }); sources.flush([]); buckets.flush([]);
    summary.flush({ message: 'Service temporarily unavailable' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Dashboard could not load');
    expect(page.textContent).toContain('Service temporarily unavailable');
    (page.querySelector('.state-box button') as HTMLButtonElement).click();
    respond(emptySummary);
    await fixture.whenStable();
    expect(page.textContent).toContain('No leads yet');
    expect(page.textContent).not.toContain('Dashboard could not load');
  });
});
