import { Routes } from '@angular/router';
import { Login } from './login/login';
import { LoginCallback } from './login/login-callback';

export const authenticationRoutes: Routes = [
  { path: 'login', component: Login },
  { path: 'login/callback', component: LoginCallback },
];
