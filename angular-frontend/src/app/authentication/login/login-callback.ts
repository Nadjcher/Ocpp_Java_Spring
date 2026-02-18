import { Component, inject, OnInit, signal } from '@angular/core';
import { AuthenticationService } from '../authentication.service';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  selector: 'app-login-callback',
  imports: [RouterLink],
  template: `
    @if (error()) {
      <div class="callback-error">
        <h2>Erreur d'authentification</h2>
        <p>{{ error() }}</p>
        <p>Le token n'a pas ete valide par la gateway.</p>
        <a routerLink="/login">Reessayer</a>
      </div>
    } @else {
      <div class="callback-loading">
        <p>Authentification en cours...</p>
        <p>Verification du token aupres de la gateway...</p>
      </div>
    }
  `,
  styles: [`
    .callback-loading, .callback-error {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 50vh;
    }
    .callback-error { color: red; }
  `]
})
export class LoginCallback implements OnInit {
  private authenticationService = inject(AuthenticationService);
  private route = inject(ActivatedRoute);

  error = signal<string | null>(null);

  ngOnInit(): void {
    const code = this.route.snapshot.queryParams['code'];
    if (code) {
      this.authenticationService.completeLogin(code).subscribe(valid => {
        if (!valid) {
          this.error.set('Le token retourne par Cognito a ete rejete par la gateway (HTTP 401/403).');
        }
        // Si valid = true, le service a deja redirige vers /simulator
      });
    } else {
      this.error.set('Aucun code d\'autorisation recu de Cognito.');
    }
  }
}
