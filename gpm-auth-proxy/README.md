# GPM Auth Proxy

Proxy d'authentification automatique pour le simulateur GPM.

## Fonctionnalites

- Obtient automatiquement un token Cognito (client_credentials)
- Proxy transparent vers le simulateur avec support WebSocket
- Injecte le token via `TokenService.setToken()` dans la page HTML
- Proxy les appels `/apigw/*` avec le header `Authorization: Bearer`
- Auto-refresh du token avant expiration

## Installation

```bash
cd gpm-auth-proxy
npm install
```

## Configuration

Copier `.env.example` vers `.env` et configurer les variables.

## Utilisation

1. Demarrer le simulateur sur le port 3003 :
   ```bash
   cd ../Simumultifunc/frontend
   npm run dev:ui -- --port 3003
   ```

2. Demarrer le proxy :
   ```bash
   cd gpm-auth-proxy
   npm start
   ```

3. Ouvrir http://localhost:8080

Le simulateur se charge avec le token automatiquement injecte.

## Variables d'environnement

| Variable | Description | Default |
|----------|-------------|---------|
| `COGNITO_TOKEN_URL` | URL du token Cognito | - |
| `COGNITO_CLIENT_ID` | Client ID Cognito | - |
| `COGNITO_CLIENT_SECRET` | Client Secret Cognito | - |
| `SIMULATOR_URL` | URL du simulateur | `http://localhost:3003` |
| `API_TARGET_URL` | URL de l'API cible | `https://pp.total-ev-charge.com` |
| `PROXY_PORT` | Port du proxy | `8080` |
