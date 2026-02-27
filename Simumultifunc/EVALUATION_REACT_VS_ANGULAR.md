# Evaluation : React vs Angular — Faut-il garder les deux frontends ?

**Date** : 27 fevrier 2026
**Projet** : GPM / EVSE Simulator (Ocpp_Java_Spring)
**Auteur** : Evaluation technique automatisee

---

## 1. Contexte

Le projet dispose actuellement de deux frontends :
- `Simumultifunc/frontend/` — React (Vite + React 18 + TypeScript)
- `Simumultifunc/angular-frontend/` — Angular 20 + TypeScript

Ce document evalue l'interet de conserver les deux en parallele.

---

## 2. Etat des lieux

### 2.1 Metriques quantitatives

| Critere                     | React (`frontend/`)          | Angular (`angular-frontend/`)  |
|-----------------------------|------------------------------|--------------------------------|
| Fichiers source             | **163**                      | **14**                         |
| Lignes de code (src)        | **~58 900**                  | **~15 400** (dont configs)     |
| Composants UI               | **87**                       | **3** (Simulator, NotFound, Unauthorized) |
| Modules / tabs              | **13**                       | **0**                          |
| Services metier             | **20**                       | **0** (auth uniquement)        |
| Version framework           | React 18.3                   | Angular 20.0                   |
| Build tool                  | Vite 5.4                     | Angular CLI 20.0               |
| State management            | Zustand 5.0                  | _(non implemente)_             |
| Styling                     | TailwindCSS 3.4              | CSS inline                     |
| Charting                    | Recharts 2.15                | _(non implemente)_             |
| i18n                        | _(non implemente)_           | Transloco 7.0 (fr-FR, en-GB)  |
| Authentification            | TokenService (cookie JWT)    | TokenService + Guard + Interceptor |

### 2.2 Fonctionnalites implementees

#### React — Frontend principal (COMPLET)

| Module               | Description                                           | Statut      |
|----------------------|-------------------------------------------------------|-------------|
| Simul GPM            | Simulation multi-bornes GPM                           | Operationnel |
| GPM Dry-Run          | Test sans execution reelle                            | Operationnel |
| Simu EVSE            | Simulation bornes de recharge EVSE                    | Operationnel |
| Smart Charging       | Profils de charge OCPP 1.6                            | Operationnel |
| OCPP Messages        | Monitoring et analyse des messages OCPP               | Operationnel |
| Perf OCPP (HTTP)     | Tests de performance HTTP                             | Operationnel |
| Perf OCPP (WS)       | Tests de performance WebSocket (25k+ connexions)      | Operationnel |
| TNR                  | Tests de non-regression automatises                   | Operationnel |
| OCPI Tests           | Tests du protocole OCPI                               | Operationnel |
| ML Analysis          | Analyse Machine Learning des sessions                 | Operationnel |
| Scheduler            | Planification de taches automatisees                  | Operationnel |
| Settings (TTE)       | Configuration de l'environnement                      | Operationnel |
| Session Overview     | Vue d'ensemble des sessions de charge                 | Operationnel |

**Total : 13 modules fonctionnels**

#### Angular — Frontend secondaire (SQUELETTE)

| Module               | Description                                           | Statut      |
|----------------------|-------------------------------------------------------|-------------|
| Authentification     | Guard + Interceptor + TokenService                    | Operationnel |
| Page Simulator       | Conteneur vide (`TODO: Ajouter les composants`)       | Placeholder |
| Page 401             | Redirection vers EVP pour authentification             | Operationnel |
| Page 404             | Page introuvable avec i18n                            | Operationnel |

**Total : 1 module fonctionnel (auth), 3 pages utilitaires**

---

## 3. Analyse comparative

### 3.1 Maturite et couverture fonctionnelle

```
React    [========================================] 100%  (13/13 modules)
Angular  [==                                      ]   5%  (0/13 modules + auth)
```

Le frontend React couvre **l'integralite des fonctionnalites metier**. Le frontend Angular ne contient que l'infrastructure d'authentification et des pages utilitaires (401, 404).

### 3.2 Authentification — Comparaison detaillee

Les deux frontends utilisent le **meme mecanisme** :

| Aspect             | React                         | Angular                         |
|--------------------|-------------------------------|---------------------------------|
| Cookie lu          | `evp_access_token`            | `evp_access_token`              |
| Validation JWT     | decode base64 + check `exp`   | decode base64 + check `exp`     |
| Redirection 401    | Page dediee + lien EVP        | Page dediee + lien EVP          |
| Dev bypass         | `localStorage` flag           | `setToken()` en console         |
| Intercepteur HTTP  | _(pas d'intercepteur global)_ | `authInterceptor` (Bearer)      |
| Route guard        | `AuthProvider` context         | `authGuard` (canActivate)       |

**Verdict** : L'Angular apporte un intercepteur HTTP (`authInterceptor`) qui injecte automatiquement le Bearer token sur les appels `/apigw/`. Ce pattern est absent cote React mais facile a implementer (intercepteur Axios).

### 3.3 Internationalisation (i18n)

| Aspect            | React                    | Angular                          |
|-------------------|--------------------------|----------------------------------|
| Librairie         | _(aucune)_               | Transloco 7.0                    |
| Langues           | _(aucune)_               | fr-FR, en-GB                     |
| Cles traduites    | 0                        | 3 (PAGE_NOT_FOUND uniquement)    |

**Verdict** : L'i18n Angular est fonctionnelle mais ne couvre que 3 cles de traduction pour la page 404. L'effort de traduction reel reste entierement a faire quel que soit le framework choisi.

### 3.4 Architecture technique

| Aspect                   | React                              | Angular                           |
|--------------------------|------------------------------------|------------------------------------|
| Bundler                  | Vite (rapide, HMR instantane)      | Angular CLI (Webpack/esbuild)      |
| Paradigme                | Fonctionnel (hooks)                | Composants classes + DI            |
| Change detection         | Virtual DOM                        | Zoneless (signals-ready)           |
| Proxy backend dev        | vite.config.ts (4 proxies)         | proxy.conf.json (1 proxy)          |
| WebSocket                | WebSocketManager custom            | _(non implemente)_                 |
| Tests unitaires          | _(non configure)_                  | Karma/Jasmine (configure)          |

---

## 4. Analyse cout/benefice

### 4.1 Cout de maintien des deux frontends

| Poste de cout                          | Estimation                                    |
|----------------------------------------|------------------------------------------------|
| Duplication de chaque nouvelle feature | x2 effort dev par feature                      |
| Double pipeline CI/CD                  | Configuration et maintenance supplementaire    |
| Double montee de version (framework)   | Angular et React evoluent independamment        |
| Double gestion des bugs                | Un bug peut exister dans un frontend et pas l'autre |
| Competences equipe                     | Maitrise React ET Angular requise              |
| Double jeu de tests                    | Tests a ecrire et maintenir en double          |

### 4.2 Benefice potentiel d'Angular

| Benefice                               | Realite                                        |
|----------------------------------------|------------------------------------------------|
| i18n avec Transloco                    | 3 cles traduites, quasi vide                   |
| Auth interceptor propre                | Reproductible en 20 lignes avec Axios          |
| Angular 20 (derniere version)          | Moderne mais le projet est vide                |
| Tests Karma pre-configures            | Aucun test ecrit                               |
| Zoneless change detection              | Avantage reel uniquement si l'app est construite |

---

## 5. Scenarios possibles

### Scenario A : Garder React uniquement (RECOMMANDE)

**Actions** :
1. Conserver `frontend/` (React) comme frontend unique
2. Porter l'intercepteur HTTP Angular dans un intercepteur Axios
3. Ajouter `react-i18next` ou `react-intl` si l'i18n devient necessaire
4. Archiver `angular-frontend/` (suppression ou branche archive)

**Avantages** :
- Zero duplication, une seule codebase a maintenir
- 13 modules deja fonctionnels
- Ecosysteme React/Vite tres performant
- Equipe concentree sur un seul framework

**Risques** :
- Si une exigence corporate impose Angular a terme, il faudra migrer

**Effort estime** : ~0.5 jour (port auth interceptor + archivage)

---

### Scenario B : Migrer tout vers Angular

**Actions** :
1. Reecrire les 13 modules React en Angular
2. Porter les 87 composants, 20 services, 13 tabs
3. Reimplementer WebSocket, Zustand → RxJS, Recharts → alternative Angular
4. Supprimer `frontend/` une fois la migration terminee

**Avantages** :
- Framework structure avec DI, intercepteurs, guards natifs
- i18n integree (Transloco)
- Tests unitaires pre-configures (Karma)
- Coherence si le reste de l'ecosysteme de l'entreprise est Angular

**Risques** :
- Effort considerable : ~58 900 lignes a reecrire
- Regression fonctionnelle pendant la migration
- Pas de valeur ajoutee fonctionnelle (meme resultat, framework different)

**Effort estime** : 3 a 6 semaines (selon taille equipe)

---

### Scenario C : Garder les deux (NON RECOMMANDE)

**Actions** :
1. Continuer a developper les deux frontends en parallele
2. Implementer progressivement les 13 modules dans Angular

**Avantages** :
- Aucun (les deux frontends ciblent la meme application)

**Risques** :
- Double cout de developpement permanent
- Desynchronisation fonctionnelle entre les deux UI
- Confusion pour les utilisateurs et les developpeurs
- Maintenance technique doublee

**Effort estime** : Cout permanent x2

---

## 6. Matrice de decision

| Critere (poids)                  | React seul | Migration Angular | Garder les deux |
|----------------------------------|:----------:|:-----------------:|:---------------:|
| Fonctionnalites existantes (30%) | +++        | -                 | ++              |
| Cout de maintenance (25%)        | +++        | ++                | -               |
| Effort de mise en oeuvre (20%)   | +++        | -                 | --              |
| Coherence architecture (15%)    | ++         | +++               | -               |
| Risque technique (10%)          | +++        | --                | --              |
| **Score pondere**                | **9.1/10** | **4.3/10**        | **2.0/10**      |

---

## 7. Recommandation

### **Garder React uniquement (Scenario A)**

Le frontend React est complet, fonctionnel et couvre l'ensemble des besoins metier. Le frontend Angular ne contient que de l'infrastructure d'authentification (reproductible en quelques lignes cote React) et une page vide.

**Exception** : Si une directive d'entreprise impose Angular comme standard technologique, le Scenario B (migration) serait a envisager, mais il represente un investissement significatif sans gain fonctionnel.

### Actions immediates recommandees

1. **Porter l'intercepteur Bearer token** d'Angular vers un intercepteur Axios dans React (~20 lignes)
2. **Archiver** `angular-frontend/` dans une branche `archive/angular-frontend`
3. **Evaluer le besoin i18n** : si necessaire, integrer `react-i18next` dans le frontend React

---

## 8. Annexes

### A. Arborescence du frontend React

```
frontend/src/
├── auth/              (3 fichiers — AuthContext, TokenService)
├── components/        (87 fichiers — composants UI)
│   ├── anomaly/       (detection d'anomalies)
│   ├── evse/          (simulation EVSE)
│   ├── gpm/           (simulation GPM)
│   ├── ocpi/          (tests OCPI)
│   ├── scheduler/     (planification)
│   ├── session/       (gestion sessions)
│   ├── simu-evse/     (simulation EVSE avancee)
│   ├── tte/           (configuration TTE)
│   └── ui/            (composants UI generiques)
├── tabs/              (13 fichiers — onglets principaux)
├── services/          (20 fichiers — API, WebSocket, TNR, OCPI...)
├── store/             (state management Zustand)
├── hooks/             (hooks personnalises)
├── types/             (definitions TypeScript)
└── contexts/          (contextes React)
```

### B. Arborescence du frontend Angular

```
angular-frontend/src/
├── app/
│   ├── authentication/    (1 fichier — auth.guard.ts)
│   ├── core/
│   │   ├── auth/          (2 fichiers — interceptor, token service)
│   │   └── config/        (2 fichiers — config vide, environment model vide)
│   ├── simulator/         (1 fichier — composant vide avec TODO)
│   ├── not-found/         (1 fichier — page 404)
│   ├── unauthorized/      (1 fichier — page 401)
│   ├── app.ts             (root component)
│   ├── app.config.ts      (providers)
│   ├── app.routes.ts      (routing)
│   └── transloco-loader.ts
└── public/
    ├── i18n/              (fr-FR.json, en-GB.json — 3 cles chacun)
    └── env.example.js     (configuration vide)
```
