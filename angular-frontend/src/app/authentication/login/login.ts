import { Component, inject } from '@angular/core';
import { IdentityProvider } from '../../core/config/identity-provider-config.model';
import { AuthenticationService } from '../authentication.service';
import { ButtonComponent } from '@totalenergiescode/tds-angular';
import { Config } from '../../core/config/config';
import { UpperCasePipe } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-login',
  imports: [ButtonComponent, UpperCasePipe, TranslocoPipe],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private authenticationService = inject(AuthenticationService);
  protected identityProviders = inject(Config).authentication.identityProviders;

  protected login(identityProvider: IdentityProvider): void {
    this.authenticationService.initiateLogin(identityProvider);
  }
}
