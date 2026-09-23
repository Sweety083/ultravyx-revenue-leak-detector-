import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { A11yModule } from '@angular/cdk/a11y';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, Subject, Subscription } from 'rxjs';
import { ApiService, downloadBlob, errorMessage, Lead, LEAD_STATUSES, LEAK_LABELS, LEAK_TYPES, PagedResponse, SourcePerformance } from './api';
import { DISPLAY_CURRENCY } from './display-config';
import { CreateLeadComponent } from './create-lead';
import { ToastService } from './toast';

@Component({ selector: 'app-leads', standalone: true, imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, A11yModule], templateUrl: './leads.html', styleUrl: './leads.css' })
export class LeadsComponent implements OnInit {
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private toast = inject(ToastService);
  private cdr = inject(ChangeDetectorRef);
  private listRequest?: Subscription;
  private detailRequest?: Subscription;
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  private searchInput = new Subject<number>();
  private searchRevision = 0;
  readonly statuses = LEAD_STATUSES;
  readonly leakTypes = LEAK_TYPES;
  readonly labels = LEAK_LABELS;
  readonly currency = DISPLAY_CURRENCY;
  sources: SourcePerformance[] = [];
  result?: PagedResponse<Lead>;
  loading = true;
  error = '';
  search = '';
  status = '';
  source = '';
  leakType = '';
  sort = 'createdAt,desc';
  page = 0;
  pageSize = 20;
  detail?: Lead;
  detailLoading = false;
  detailError = '';
  exporting = false;

  ngOnInit(): void {
    this.loadSources();
    this.searchInput.pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef)).subscribe(revision => { if (revision === this.searchRevision) this.applyFilters(); });
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.searchRevision++;
      this.search = params.get('search') || '';
      this.status = params.get('status') || '';
      this.source = params.get('source') || '';
      this.leakType = params.get('leakType') || '';
      this.sort = params.get('sort') || 'createdAt,desc';
      const requestedPage = Number(params.get('page') || '0');
      this.page = Number.isInteger(requestedPage) && requestedPage >= 0 && requestedPage <= 2147483647 ? requestedPage : 0;
      this.load();
    });
  }
  onSearch(event: Event): void { this.search = (event.target as HTMLInputElement).value; this.searchInput.next(++this.searchRevision); }
  clearFilters(): void { this.searchRevision++; this.search = ''; this.status = ''; this.source = ''; this.leakType = ''; this.sort = 'createdAt,desc'; this.page = 0; this.navigate(); }
  private loadSources(): void { this.api.sources().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: sources => { this.sources = sources; this.cdr.markForCheck(); }, error: () => { this.sources = []; this.cdr.markForCheck(); } }); }
  addLead(): void {
    this.dialog.open<CreateLeadComponent, undefined, Lead>(CreateLeadComponent, { width: '680px', maxWidth: '96vw', autoFocus: '#new-externalLeadId' })
      .afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(lead => {
        if (!lead) return;
        this.toast.show(`${lead.name} added to your pipeline.`);
        this.load();
        this.loadSources();
        this.detail = lead;
        this.detailError = '';
        this.detailLoading = false;
        this.cdr.markForCheck();
      });
  }
  exportLeads(): void {
    if (this.exporting || this.loading || this.error || !this.result?.totalElements) return;
    this.exporting = true;
    this.api.exportLeads({ search: this.search, status: this.status, source: this.source, leakType: this.leakType, sort: this.sort })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: blob => {
          downloadBlob(blob, 'ultravyx-leads.csv');
          this.exporting = false;
          this.toast.show('CSV downloaded with all matching leads.');
          this.cdr.markForCheck();
        },
        error: error => {
          this.exporting = false;
          this.toast.show(errorMessage(error), 'error');
          this.cdr.markForCheck();
        },
      });
  }
  applyFilters(): void { this.page = 0; this.navigate(); }
  changePage(page: number): void { if (page < 0 || (this.result && page >= this.result.totalPages)) return; this.page = page; this.navigate(); }
  private navigate(): void { this.router.navigate(['/leads'], { queryParams: { search: this.search || null, status: this.status || null, source: this.source || null, leakType: this.leakType || null, sort: this.sort === 'createdAt,desc' ? null : this.sort, page: this.page || null } }); }
  load(): void { this.listRequest?.unsubscribe(); this.loading = true; this.error = ''; this.listRequest = this.api.leads({ search: this.search, status: this.status, source: this.source, leakType: this.leakType, sort: this.sort, page: this.page, size: this.pageSize }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: result => { this.result = result; this.loading = false; this.cdr.markForCheck(); }, error: error => { this.error = errorMessage(error); this.loading = false; this.cdr.markForCheck(); } }); }
  openDetail(id: string): void { this.detailRequest?.unsubscribe(); this.detail = undefined; this.detailError = ''; this.detailLoading = true; this.detailRequest = this.api.lead(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: lead => { this.detail = lead; this.detailLoading = false; this.cdr.markForCheck(); }, error: error => { this.detailError = errorMessage(error); this.detailLoading = false; this.cdr.markForCheck(); } }); }
  closeDetail(): void { this.detailRequest?.unsubscribe(); this.detail = undefined; this.detailError = ''; this.detailLoading = false; }
}
