import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { ApiService, downloadBlob, errorMessage, Leak, LEAK_HELP, LEAK_LABELS, LEAK_TYPES, LeakType, Lead, PagedResponse } from './api';
import { ToastService } from './toast';

@Component({ selector: 'app-leaks', standalone: true, imports: [CommonModule, RouterLink, MatButtonModule], templateUrl: './leaks.html', styleUrl: './leaks.css' })
export class LeaksComponent implements OnInit {
  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  private destroyRef = inject(DestroyRef);
  private tableRequest?: Subscription;
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private toast = inject(ToastService);
  readonly labels = LEAK_LABELS;
  readonly help = LEAK_HELP;
  readonly types = LEAK_TYPES;
  selected: LeakType = 'UNCONTACTED';
  leaks: Leak[] = [];
  leads?: PagedResponse<Lead>;
  loading = true;
  tableLoading = true;
  error = '';
  tableError = '';
  exporting = false;
  page = 0;

  ngOnInit(): void {
    this.load();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const requested = params.get('type') as LeakType | null;
      this.selected = requested && this.types.includes(requested) ? requested : 'UNCONTACTED';
      this.page = 0;
      this.loadTable();
    });
  }
  load(): void { this.loading = true; this.error = ''; this.api.leaks().subscribe({ next: leaks => { this.leaks = leaks; this.loading = false; this.cdr.markForCheck(); }, error: error => { this.error = errorMessage(error); this.loading = false; this.cdr.markForCheck(); } }); }
  select(type: LeakType): void { this.router.navigate(['/leaks'], { queryParams: { type } }); }
  loadTable(): void { this.tableRequest?.unsubscribe(); this.tableLoading = true; this.tableError = ''; this.tableRequest = this.api.leakLeads(this.selected, this.page, 10).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: leads => { this.leads = leads; this.tableLoading = false; this.cdr.markForCheck(); }, error: error => { this.tableError = errorMessage(error); this.tableLoading = false; this.cdr.markForCheck(); } }); }
  changePage(page: number): void { if (page < 0 || (this.leads && page >= this.leads.totalPages)) return; this.page = page; this.loadTable(); }
  count(type: LeakType): number { return this.leaks.find(l => l.type === type)?.count || 0; }
  export(): void { const type = this.selected; this.exporting = true; this.api.exportLeak(type).subscribe({ next: blob => { downloadBlob(blob, `ultravyx-${type.toLowerCase().replaceAll('_','-')}-leads.csv`); this.exporting = false; this.toast.show('Affected-lead CSV downloaded.'); this.cdr.markForCheck(); }, error: error => { this.exporting = false; this.toast.show(errorMessage(error), 'error'); this.cdr.markForCheck(); } }); }
}
