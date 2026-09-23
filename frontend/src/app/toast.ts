import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly message = signal('');
  readonly kind = signal<'success' | 'error'>('success');
  private timer?: ReturnType<typeof setTimeout>;
  show(message: string, kind: 'success' | 'error' = 'success'): void {
    clearTimeout(this.timer);
    this.message.set(message);
    this.kind.set(kind);
    this.timer = setTimeout(() => this.message.set(''), 4500);
  }
  dismiss(): void { clearTimeout(this.timer); this.message.set(''); }
}
