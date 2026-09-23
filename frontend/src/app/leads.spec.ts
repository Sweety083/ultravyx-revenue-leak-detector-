import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom, of, Subject } from 'rxjs';
import { ApiService, Lead, PagedResponse } from './api';
import { LeadsComponent } from './leads';
import { LeaksComponent } from './leaks';
import { CreateLeadComponent } from './create-lead';
import { ToastService } from './toast';

describe('Lead filters and gap navigation', () => {
  const emptyPage: PagedResponse<Lead> = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
  let api: { leads: ReturnType<typeof vi.fn>; sources: ReturnType<typeof vi.fn>; leaks: ReturnType<typeof vi.fn>; leakLeads: ReturnType<typeof vi.fn>; exportLeads: ReturnType<typeof vi.fn>; createLead: ReturnType<typeof vi.fn> };
  beforeEach(async () => {
    api = { leads: vi.fn(() => of(emptyPage)), sources: vi.fn(() => of([])), leaks: vi.fn(() => of([{ type: 'NO_SHOW', severity: 'HIGH', count: 1, description: 'Missed an appointment' }])), leakLeads: vi.fn(() => of(emptyPage)), exportLeads: vi.fn(), createLead: vi.fn() };
    await TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'leads', component: LeadsComponent }, { path: 'leaks', component: LeaksComponent }]), { provide: ApiService, useValue: api }] }).compileComponents();
  });
  afterEach(() => { TestBed.inject(MatDialog).closeAll(); TestBed.inject(ToastService).dismiss(); });

  it('exports matching rows across pages and permits retry after an API failure', async () => {
    api.leads.mockReturnValue(of({ ...emptyPage, totalElements: 45, totalPages: 3 }));
    const download = new Subject<Blob>();
    api.exportLeads.mockReturnValue(download.asObservable());
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/leads?status=WON&source=Referral&sort=name,asc&page=2', LeadsComponent);
    component.exportLeads();
    component.exportLeads();
    expect(api.exportLeads).toHaveBeenCalledOnce();
    expect(api.exportLeads).toHaveBeenCalledWith({ search: '', status: 'WON', source: 'Referral', leakType: '', sort: 'name,asc' });
    expect(component.exporting).toBe(true);
    download.error({ status: 502 });
    expect(component.exporting).toBe(false);
    expect(TestBed.inject(ToastService).message()).toContain('Cannot reach the API');
    api.exportLeads.mockReturnValue(new Subject<Blob>());
    component.exportLeads();
    expect(api.exportLeads).toHaveBeenCalledTimes(2);
  });

  it('does not export an empty or loading lead list', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/leads', LeadsComponent);
    component.exportLeads();
    expect(api.exportLeads).not.toHaveBeenCalled();
    component.result = { ...emptyPage, totalElements: 1 };
    component.loading = true;
    component.exportLeads();
    expect(api.exportLeads).not.toHaveBeenCalled();
  });

  it('opens Add lead, saves, refreshes and shows the saved details while retaining filters', async () => {
    const saved: Lead = { id: 'saved-id', externalLeadId: 'MANUAL-001', name: 'Asha Rao', status: 'NEW', createdAt: '2026-09-23T04:00:00Z', contactedAt: null, assignedTo: null, source: 'Referral', campaign: null, appointmentAt: null, attendedAt: null, revenue: null, detectedProblems: [{ type: 'UNASSIGNED', severity: 'MEDIUM' }] };
    api.createLead.mockReturnValue(of(saved));
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/leads?status=WON', LeadsComponent);
    (harness.routeNativeElement!.querySelector('.header-actions button') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    const dialog = TestBed.inject(MatDialog).openDialogs[0];
    const form = dialog.componentInstance as CreateLeadComponent;
    form.form.patchValue({ externalLeadId: 'MANUAL-001', name: 'Asha Rao', source: 'Referral' });
    const closed = firstValueFrom(dialog.afterClosed());
    form.save();
    await closed;
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(api.createLead).toHaveBeenCalledOnce();
    expect(api.leads).toHaveBeenCalledTimes(2);
    expect(api.sources).toHaveBeenCalledTimes(2);
    expect(TestBed.inject(Router).url).toBe('/leads?status=WON');
    expect(component.detail).toEqual(saved);
    expect(harness.routeNativeElement!.querySelector('[aria-label="Lead details"]')?.textContent).toContain('Asha Rao');
    expect(TestBed.inject(ToastService).message()).toContain('Asha Rao added');
    harness.routeNativeElement!.querySelector('[aria-label="Lead details"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(component.detail).toBeUndefined();
  });

  it('cancels Add lead without creating a record or refreshing the list', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/leads', LeadsComponent);
    component.addLead();
    await harness.fixture.whenStable();
    const dialog = TestBed.inject(MatDialog).openDialogs[0];
    const closed = firstValueFrom(dialog.afterClosed());
    dialog.close();
    await closed;
    await harness.fixture.whenStable();
    expect(api.createLead).not.toHaveBeenCalled();
    expect(api.leads).toHaveBeenCalledTimes(1);
    expect(component.detail).toBeUndefined();
  });

  it('restores all filters and pagination from a shareable URL', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/leads?search=Asha&status=QUALIFIED&source=Referral&leakType=UNASSIGNED&sort=name,asc&page=2', LeadsComponent);
    expect(api.leads).toHaveBeenLastCalledWith({ search: 'Asha', status: 'QUALIFIED', source: 'Referral', leakType: 'UNASSIGNED', sort: 'name,asc', page: 2, size: 20 });
    expect(component.page).toBe(2);
    expect(harness.routeNativeElement?.textContent).toContain('Try clearing one or more filters.');
  });

  it('resets pagination when a filter is changed and reloads the URL state', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/leads?search=Asha&page=2', LeadsComponent);
    component.status = 'WON';
    component.applyFilters();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/leads?search=Asha&status=WON');
    expect(api.leads).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'Asha', status: 'WON', page: 0 }));
  });

  it('ignores pagination requests outside the available result pages', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/leads', LeadsComponent);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    component.changePage(-1);
    component.changePage(1);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('clears filters and cancels a pending search instead of restoring it later', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/leads?status=WON&sort=name,asc&page=2', LeadsComponent);
    const search = harness.routeNativeElement!.querySelector('input')!;
    search.value = 'Asha';
    search.dispatchEvent(new Event('input'));
    component.clearFilters();
    await harness.fixture.whenStable();
    await new Promise(resolve => setTimeout(resolve, 350));
    expect(TestBed.inject(Router).url).toBe('/leads');
    expect(api.leads).toHaveBeenLastCalledWith({ search: '', status: '', source: '', leakType: '', sort: 'createdAt,desc', page: 0, size: 20 });
  });

  it.each(['1.5', 'Infinity', '-1', '2147483648'])('normalizes invalid URL page %s', async page => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(`/leads?page=${page}`, LeadsComponent);
    expect(component.page).toBe(0);
    expect(api.leads).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 }));
  });

  it('prevents an older query response from replacing a newly filtered result', async () => {
    const original = new Subject<PagedResponse<Lead>>();
    const filtered = new Subject<PagedResponse<Lead>>();
    api.leads.mockReturnValueOnce(original.asObservable()).mockReturnValueOnce(filtered.asObservable());
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/leads', LeadsComponent);
    const component = await harness.navigateByUrl('/leads?status=WON', LeadsComponent);
    filtered.next({ ...emptyPage, totalElements: 8, totalPages: 1 });
    original.next({ ...emptyPage, totalElements: 99, totalPages: 5 });
    expect(original.observed).toBe(false);
    expect(component.result?.totalElements).toBe(8);
    expect(component.status).toBe('WON');
    original.complete(); filtered.complete();
  });

  it('opens a selected process gap in the leads table with its filter preserved', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/leaks?type=NO_SHOW', LeaksComponent);
    expect(api.leakLeads).toHaveBeenLastCalledWith('NO_SHOW', 0, 10);
    const link = harness.routeNativeElement?.querySelector('a[href="/leads?leakType=NO_SHOW"]') as HTMLAnchorElement;
    expect(link).not.toBeNull();
    link.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/leads?leakType=NO_SHOW');
    expect(api.leads).toHaveBeenLastCalledWith(expect.objectContaining({ leakType: 'NO_SHOW', page: 0 }));
  });

  it('falls back to the first gap when an unknown type appears in the URL', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/leaks?type=NOT_A_GAP', LeaksComponent);
    expect(component.selected).toBe('UNCONTACTED');
    expect(api.leakLeads).toHaveBeenLastCalledWith('UNCONTACTED', 0, 10);
  });
});
