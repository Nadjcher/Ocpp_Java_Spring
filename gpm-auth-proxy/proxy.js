require('dotenv').config();
const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const fetch = require('node-fetch');

const {
  COGNITO_TOKEN_URL,
  COGNITO_CLIENT_ID,
  COGNITO_CLIENT_SECRET,
  SIMULATOR_URL = 'http://localhost:3003',
  API_TARGET_URL = 'https://pp.total-ev-charge.com',
  PROXY_PORT = 8080
} = process.env;

let accessToken = null;
let tokenExpiresAt = 0;

// Obtenir un token Cognito
async function fetchToken() {
  console.log('Obtention du token Cognito...');
  const params = new URLSearchParams({
    grant_type: 'client_credentials',
    client_id: COGNITO_CLIENT_ID,
    client_secret: COGNITO_CLIENT_SECRET
  });

  const res = await fetch(COGNITO_TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: params
  });

  if (!res.ok) {
    throw new Error(`Cognito error: ${res.status} ${await res.text()}`);
  }

  const data = await res.json();
  accessToken = data.access_token;
  tokenExpiresAt = Date.now() + (data.expires_in - 300) * 1000; // Refresh 5min avant
  console.log(`Token obtenu (expire dans ${data.expires_in}s)`);
  return accessToken;
}

// Auto-refresh du token
async function ensureToken() {
  if (!accessToken || Date.now() > tokenExpiresAt) {
    await fetchToken();
  }
  return accessToken;
}

// Démarrer le proxy
async function startProxy() {
  await fetchToken();

  const app = express();

  // Proxy API avec injection du Bearer token
  app.use('/apigw', createProxyMiddleware({
    target: API_TARGET_URL,
    changeOrigin: true,
    secure: true,
    onProxyReq: async (proxyReq) => {
      const token = await ensureToken();
      proxyReq.setHeader('Authorization', `Bearer ${token}`);
    },
    logLevel: 'warn'
  }));

  // Proxy WebSocket vers le simulateur
  const wsProxy = createProxyMiddleware({
    target: SIMULATOR_URL,
    changeOrigin: true,
    ws: true,
    logLevel: 'warn'
  });
  app.use('/ws', wsProxy);

  // Proxy HTML avec injection du token via redirect URL
  app.use('/', async (req, res, next) => {
    const accept = req.headers.accept || '';

    // Si ce n'est pas une requête HTML, proxy direct
    if (!accept.includes('text/html')) {
      return createProxyMiddleware({
        target: SIMULATOR_URL,
        changeOrigin: true,
        logLevel: 'silent'
      })(req, res, next);
    }

    // Si le token est déjà dans l'URL, proxy direct
    const url = new URL(req.url, `http://${req.headers.host}`);
    if (url.searchParams.has('token')) {
      return createProxyMiddleware({
        target: SIMULATOR_URL,
        changeOrigin: true,
        logLevel: 'silent'
      })(req, res, next);
    }

    // Redirect avec le token dans l'URL
    try {
      const token = await ensureToken();
      url.searchParams.set('token', token);
      const redirectUrl = url.pathname + url.search;
      console.log('[Proxy] Redirect avec token vers', url.pathname);
      res.redirect(302, redirectUrl);
    } catch (err) {
      console.error('[Proxy] Erreur:', err.message);
      next(err);
    }
  });

  // Démarrer le serveur avec support WebSocket
  const server = app.listen(PROXY_PORT, () => {
    console.log(`\nProxy demarre sur http://localhost:${PROXY_PORT}`);
    console.log(`   Simulateur : ${SIMULATOR_URL} (+ WebSocket)`);
    console.log(`   API cible   : ${API_TARGET_URL}`);
    console.log(`   Token auto-injecte via redirect URL (?token=...)\n`);
  });

  // Upgrade WebSocket
  server.on('upgrade', wsProxy.upgrade);

  // Auto-refresh token toutes les 30 minutes
  setInterval(() => ensureToken().catch(console.error), 30 * 60 * 1000);
}

startProxy().catch(err => {
  console.error('Erreur fatale:', err);
  process.exit(1);
});
