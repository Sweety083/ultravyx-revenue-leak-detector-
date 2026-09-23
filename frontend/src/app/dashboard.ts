import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage, Funnel, LEAK_HELP, LEAK_LABELS, LeakType, ResponseBucket, SourcePerformance, Summary } from './api';
import { DISPLAY_CURRENCY } from './display-config';

@Component({
  selector: 'app-dashboard', standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule],
  templateUrl: './dashboard.html', styleUrl: './dashboard.css',
})
export class DashboardComponent implements OnInit {
  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  readonly labels = LEAK_LABELS;
  readonly help = LEAK_HELP;
  readonly currency = DISPLAY_CURRENCY;
  loading = true;
  error = '';
  summary?: Summary;
  funnel?: Funnel;
  sources: SourcePerformance[] = [];
  buckets: ResponseBucket[] = [];

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading = true; this.error = '';
    forkJoin({ summary: this.api.summary(), funnel: this.api.funnel(), sources: this.api.sources(), buckets: this.api.responseTimes() }).subscribe({
      next: ({ summary, funnel, sources, buckets }) => { this.summary = summary; this.funnel = funnel; this.sources = sources; this.buckets = buckets; this.loading = false; this.cdr.markForCheck(); },
      error: error => { this.error = errorMessage(error); this.loading = false; this.cdr.markForCheck(); },
    });
  }
  leakLabel(type: LeakType): string { return this.labels[type]; }
  leakHelp(type: LeakType): string { return this.help[type]; }
  funnelWidth(count: number): number { return count > 0 && this.summary?.totalLeads ? Math.max(3, Math.round(count / this.summary.totalLeads * 100)) : 0; }
  bucketWidth(count: number): number { const max = Math.max(1, ...this.buckets.map(b => b.count)); return Math.round(count / max * 100); }
}
