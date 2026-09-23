import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { ApiService, errorMessage, UploadResult } from './api';
import { ToastService } from './toast';

@Component({ selector: 'app-upload', standalone: true, imports: [CommonModule, RouterLink, MatButtonModule], templateUrl: './upload.html', styleUrl: './upload.css' })
export class UploadComponent {
  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  private toast = inject(ToastService);
  file?: File;
  progress = 0;
  importing = false;
  result?: UploadResult;
  error = '';
  dragOver = false;

  choose(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectFile(input.files?.[0]);
    input.value = '';
  }
  dropped(event: DragEvent): void { event.preventDefault(); this.dragOver = false; this.selectFile(event.dataTransfer?.files[0]); }
  selectFile(file?: File): void {
    if (!file || this.importing) return;
    this.cdr.markForCheck();
    this.result = undefined; this.progress = 0;
    if (!file.name.toLowerCase().endsWith('.csv')) { this.error = 'Please select a .csv file.'; this.file = undefined; return; }
    if (!file.size) { this.error = 'This CSV file is empty. Choose a file with a header and lead rows.'; this.file = undefined; return; }
    if (file.size > 10 * 1024 * 1024) { this.error = 'CSV exceeds the 10 MB upload limit.'; this.file = undefined; return; }
    this.file = file; this.error = ''; this.result = undefined; this.progress = 0;
  }
  upload(): void {
    if (!this.file || this.importing) return;
    this.error = ''; this.importing = true; this.progress = 0; this.result = undefined;
    this.api.upload(this.file).subscribe({
      next: event => {
        if (event.type === HttpEventType.UploadProgress && event.total) this.progress = Math.round(100 * event.loaded / event.total);
        if (event.type === HttpEventType.Response) {
          this.result = event.body || undefined; this.importing = false; this.progress = 100;
          if (this.result) this.toast.show(`${this.result.inserted} leads added, ${this.result.updated} updated${this.result.failed ? `, ${this.result.failed} failed` : ''}.`, this.result.failed ? 'error' : 'success');
        }
        this.cdr.markForCheck();
      },
      error: error => { this.error = errorMessage(error); this.importing = false; this.toast.show('Import failed. Check the file and try again.', 'error'); this.cdr.markForCheck(); },
    });
  }
}
