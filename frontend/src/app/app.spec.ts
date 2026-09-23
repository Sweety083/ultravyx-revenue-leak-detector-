import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { ToastService } from './toast';

describe('Application shell', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] }).compileComponents();
  });

  it('provides the branded desktop and mobile navigation', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('.brand')?.textContent).toContain('ULTRAVYX');
    for (const navigation of page.querySelectorAll('nav')) {
      expect(Array.from(navigation.querySelectorAll('a')).map(a => a.getAttribute('href')))
        .toEqual(['/dashboard', '/upload', '/leaks', '/leads']);
    }
  });

  it('announces and dismisses an error toast', async () => {
    const fixture = TestBed.createComponent(App);
    const toast = TestBed.inject(ToastService);
    toast.show('Import failed. Please retry.', 'error');
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[role="status"]')?.textContent).toContain('Import failed. Please retry.');
    expect(page.querySelector('[role="status"]')?.classList.contains('error')).toBe(true);
    (page.querySelector('[aria-label="Dismiss message"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(page.querySelector('[role="status"]')).toBeNull();
  });
});
