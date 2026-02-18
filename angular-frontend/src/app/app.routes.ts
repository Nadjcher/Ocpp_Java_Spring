import { Routes } from '@angular/router';
import { authenticationRoutes } from './authentication/authentication.routes';
import { authGuard } from './authentication/auth.guard';
import { NotFound } from './not-found/not-found';

export const routes: Routes = [
  ...authenticationRoutes,
  {
    path: 'simulator',
    canActivate: [authGuard],
    loadComponent: () => import('./simulator/simulator').then(m => m.Simulator),
  },
  { path: '', redirectTo: 'simulator', pathMatch: 'full' },
  { path: '**', component: NotFound },
];
