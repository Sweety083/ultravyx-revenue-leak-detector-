import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', loadComponent: () => import('./dashboard').then(m => m.DashboardComponent), title: 'Dashboard | ULTRAVYX' },
  { path: 'upload', loadComponent: () => import('./upload').then(m => m.UploadComponent), title: 'Upload leads | ULTRAVYX' },
  { path: 'leaks', loadComponent: () => import('./leaks').then(m => m.LeaksComponent), title: 'Process gaps | ULTRAVYX' },
  { path: 'leads', loadComponent: () => import('./leads').then(m => m.LeadsComponent), title: 'All leads | ULTRAVYX' },
  { path: '**', redirectTo: 'dashboard' },
];
