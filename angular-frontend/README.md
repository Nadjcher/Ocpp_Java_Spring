# GPM Simulator - Angular Frontend

Frontend Angular 20 pour le simulateur GPM avec authentification OAuth2 via AWS Cognito.

## Architecture d'authentification

```
Utilisateur -> /login -> Clic "Entra ID" ou "Total Connect BTB"
    |
Redirect -> Cognito /oauth2/authorize
    |
Cognito authentifie -> redirect vers /login/callback?code=XXXX
    |
LoginCallback -> GET /apigw/auth/token?code=XXX
    |
API Gateway echange le code -> retourne token
    |
Verification Gateway -> GET /apigw/energy-service-manager/tree-roots
    |
200 = OK -> Redirect vers /simulator
401 = KO -> Afficher erreur + retour /login
```

## Installation

```bash
cd angular-frontend
npm install
```

## Configuration

1. Copier `public/env.example.js` vers `public/env.js`
2. Configurer les `clientId` Cognito dans `public/env.js`

## Developpement

```bash
npm start
```

Ouvrir http://localhost:4200

## Structure

```
src/app/
|-- authentication/           # Module d'authentification
|   |-- login/                # Page de login
|   |-- models/               # Modeles (AuthenticationUrl)
|   |-- auth.guard.ts         # Guard de protection des routes
|   |-- authentication.service.ts  # Service principal
|   |-- authentication.routes.ts   # Routes auth
|-- core/
|   |-- config/               # Configuration (env, Cognito)
|   |-- interceptors/         # Intercepteur HTTP (Bearer token)
|-- simulator/                # Pages du simulateur
|-- not-found/                # Page 404
```

## Flux de verification du token

### Niveau 1 - Cote client
`isAuthenticated()` decode le JWT et verifie `exp`.

### Niveau 2 - Cote gateway
`verifyToken()` appelle `GET /apigw/energy-service-manager/tree-roots`.

### Niveau 3 - Protection continue
`authInterceptor` intercepte les 401/403 et force un logout.
