# Configuration Base de Donnees MongoDB - EVSE Simulator

## Vue d'ensemble

Le projet utilise MongoDB comme base de donnees NoSQL pour stocker les sessions, profils vehicules, scenarios TNR et taches planifiees.

## Demarrage rapide

### 1. Demarrer MongoDB avec Docker

```bash
cd backend
docker-compose up -d
```

Cela demarre :
- MongoDB sur le port 27017
- (Optionnel) Mongo Express sur http://localhost:8081

Pour demarrer avec Mongo Express (interface web) :
```bash
docker-compose --profile tools up -d
```

### 2. Lancer l'application

```bash
mvn spring-boot:run
```

L'application se connecte automatiquement a MongoDB.

## Configuration

### Variables d'environnement

| Variable | Description | Valeur par defaut |
|----------|-------------|-------------------|
| MONGODB_URI | URI de connexion MongoDB | mongodb://localhost:27017/evse_simulator |
| MONGODB_DATABASE | Nom de la base de donnees | evse_simulator |

### Configuration Spring Boot

Dans `application.yml` :
```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/evse_simulator}
      database: ${MONGODB_DATABASE:evse_simulator}
      auto-index-creation: true
```

## Credentials par defaut

| Parametre | Valeur |
|-----------|--------|
| Host | localhost |
| Port | 27017 |
| Database | evse_simulator |
| Admin User | admin |
| Admin Password | admin123 |
| App User | evse |
| App Password | evse_secret |

## Collections MongoDB

Le script `mongo-init.js` cree automatiquement les collections suivantes :

### sessions
Stocke les sessions de charge EVSE avec documents imbriques pour logs, donnees graphiques et messages OCPP.

```javascript
{
  _id: "uuid",
  cpId: "CHARGE-POINT-001",
  state: "CHARGING",
  connected: true,
  soc: 45.5,
  currentPowerKw: 22.0,
  logs: [...],      // Embedded (max 500)
  socData: [...],   // Embedded (max 500)
  powerData: [...], // Embedded (max 500)
  ocppMessages: [...] // Embedded (max 500)
}
```

### vehicleProfiles
Profils de vehicules electriques avec specifications techniques.

```javascript
{
  _id: "tesla-model-3-lr",
  brand: "Tesla",
  model: "Model 3 Long Range",
  batteryCapacityKwh: 78.0,
  maxDcPowerKw: 250.0,
  maxAcPowerKw: 11.0,
  dcChargingCurve: { 10: 250, 50: 150, 80: 50 }
}
```

### tnrScenarios
Scenarios de test TNR.

### tnrExecutions
Historique des executions de tests TNR.

### scheduledTasks
Taches planifiees (cron, intervalle).

### taskExecutions
Historique des executions de taches.

### ocpiPartners
Partenaires OCPI pour les tests d'interoperabilite.

## Index

Les index suivants sont crees automatiquement :

### sessions
- `cpId`
- `state`
- `connected`
- `updatedAt`
- `createdAt`
- Index compose : `{cpId: 1, state: 1}`

### vehicleProfiles
- `brand`
- `name`
- `active`

### tnrScenarios
- `name`
- `category`
- `status`
- `active`

### scheduledTasks
- `enabled`
- `nextRunAt`

## Documents MongoDB

Les classes Document sont dans le package `com.evse.simulator.document` :

| Document | Description |
|----------|-------------|
| SessionDocument | Session de charge avec embedded logs/charts |
| VehicleProfileDocument | Profil vehicule EV |
| TnrScenarioDocument | Scenario de test TNR |
| TnrExecutionDocument | Execution de test TNR |
| ScheduledTaskDocument | Tache planifiee |
| OcpiPartnerDocument | Partenaire OCPI |

## Repositories

Les repositories Spring Data MongoDB sont dans `com.evse.simulator.repository.mongo` :

| Repository | Methodes principales |
|------------|---------------------|
| SessionMongoRepository | findByCpId, findByState, findActiveSessions |
| VehicleProfileMongoRepository | findByBrand, searchByKeyword |
| TnrScenarioMongoRepository | findByCategory, findByTag |
| TnrExecutionMongoRepository | findByScenarioId, findLatestByScenarioId |
| ScheduledTaskMongoRepository | findDueTasks, findByEnabledTrue |
| OcpiPartnerMongoRepository | findByCountryCodeAndPartyId |

## Mapper

Le `SessionDocumentMapper` dans `com.evse.simulator.document.mapper` permet de convertir entre les modeles existants et les documents MongoDB.

```java
@Autowired
SessionDocumentMapper mapper;

// Model vers Document
SessionDocument doc = mapper.toDocument(session);

// Document vers Model
Session session = mapper.toModel(doc);
```

## Interface d'administration Mongo Express

Accessible a http://localhost:8081 (avec profile tools)

- Username: admin
- Password: admin123

## Commandes utiles

### Verifier les collections
```bash
docker exec -it evse-mongodb mongosh -u admin -p admin123 --authenticationDatabase admin evse_simulator --eval "show collections"
```

### Compter les documents
```bash
docker exec -it evse-mongodb mongosh -u admin -p admin123 --authenticationDatabase admin evse_simulator --eval "db.sessions.countDocuments()"
```

### Exporter les donnees
```bash
docker exec -it evse-mongodb mongodump -u admin -p admin123 --authenticationDatabase admin --db evse_simulator --out /data/backup
```

## Arret de MongoDB

```bash
# Arret simple (donnees persistees)
docker-compose down

# Arret avec suppression des donnees
docker-compose down -v
```

## Troubleshooting

### Erreur de connexion
```
Connection refused
```
Verifier que MongoDB est demarre : `docker-compose ps`

### Authentification echouee
```
Authentication failed
```
Verifier les credentials ou reinitialiser :
```bash
docker-compose down -v
docker-compose up -d
```

### Reset complet
```bash
docker-compose down -v
docker-compose up -d
```

### Logs MongoDB
```bash
docker-compose logs mongodb
```
