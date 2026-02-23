import { Routes } from '@angular/router';
import { authGuard } from './authentication/auth.guard';

export const routes: Routes = [
  {
    path: 'simulator',
    canActivate: [authGuard],
    loadComponent: () => import('./simulator/simulator').then(m => m.Simulator),
  },
  {
    path: 'unauthorized',
    loadComponent: () => import('./unauthorized/unauthorized').then(m => m.UnauthorizedComponent),
  },
  { path: '', redirectTo: 'simulator', pathMatch: 'full' },
  { path: '**', redirectTo: 'simulator' },
];
