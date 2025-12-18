# EVSE Simulator Backend - Spring Boot 3.2

Backend Spring Boot pour le simulateur EVSE OCPP 1.6, optimisé pour 25 000 connexions WebSocket simultanées.

## Prérequis

- **Java 17** ou supérieur
- **Maven 3.9+**
- **RAM**: Minimum 4 Go (8 Go recommandé pour les tests de charge)

## Démarrage rapide

### 1. Compilation

```bash
cd backend
mvn clean package -DskipTests
```

### 2. Lancement en mode développement

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Ou avec le JAR compilé :

```bash
java -jar target/evse-simulator-1.0.0.jar --spring.profiles.active=dev
```

### 3. Lancement en mode production

```bash
java -Xmx4g -jar target/evse-simulator-1.0.0.jar --spring.profiles.active=prod
```

## Configuration

### Profils disponibles

| Profil | Port | Description |
|--------|------|-------------|
| `dev` | 8081 | Développement avec CORS permissif, logs verbeux |
| `prod` | 8080 | Production avec paramètres optimisés, 50k connexions |

### Variables d'environnement

```bash
# Port du serveur
SERVER_PORT=8080

# URL du CSMS par défaut
OCPP_DEFAULT_CSMS_URL=ws://localhost:9000/ocpp

# Intervalles OCPP (en secondes)
OCPP_HEARTBEAT_INTERVAL=30
OCPP_METER_VALUES_INTERVAL=10

# Limites de performance
PERFORMANCE_MAX_SESSIONS=25000
```

### Fichiers de données

Les données sont stockées en JSON dans le dossier `data/` :

- `data/sessions.json` - Sessions de simulation
- `data/vehicles.json` - Profils de véhicules
- `data/scenarios.json` - Scénarios TNR

## API REST

### Documentation Swagger

Accédez à la documentation interactive :

- **Swagger UI**: http://localhost:8081/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8081/v3/api-docs

### Endpoints principaux

#### Sessions

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/sessions` | Liste toutes les sessions |
| POST | `/api/sessions` | Crée une nouvelle session |
| GET | `/api/sessions/{id}` | Récupère une session |
| DELETE | `/api/sessions/{id}` | Supprime une session |
| POST | `/api/sessions/{id}/connect` | Connecte au CSMS |
| POST | `/api/sessions/{id}/disconnect` | Déconnecte |
| POST | `/api/sessions/{id}/boot` | Envoie BootNotification |
| POST | `/api/sessions/{id}/authorize` | Envoie Authorize |
| POST | `/api/sessions/{id}/start` | Démarre la transaction |
| POST | `/api/sessions/{id}/stop` | Arrête la transaction |
| POST | `/api/sessions/{id}/status` | Envoie StatusNotification |
| POST | `/api/sessions/{id}/metervalues` | Envoie MeterValues |
| POST | `/api/sessions/{id}/heartbeat` | Envoie Heartbeat |

#### Véhicules

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/vehicles` | Liste tous les véhicules |
| POST | `/api/vehicles` | Crée un profil véhicule |
| GET | `/api/vehicles/{id}` | Récupère un véhicule |
| PUT | `/api/vehicles/{id}` | Met à jour un véhicule |
| DELETE | `/api/vehicles/{id}` | Supprime un véhicule |
| GET | `/api/vehicles/search` | Recherche par fabricant/connecteur |

#### Smart Charging (OCPP 1.6)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/smart-charging/{sessionId}/profile` | Définit un profil de charge |
| DELETE | `/api/smart-charging/{sessionId}/profile` | Supprime un profil |
| GET | `/api/smart-charging/{sessionId}/profiles` | Liste les profils actifs |
| GET | `/api/smart-charging/{sessionId}/composite-schedule` | Récupère le schedule composite |

#### Opérations en lot

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/batch/create-sessions` | Crée N sessions |
| POST | `/api/batch/connect-all` | Connecte toutes les sessions |
| POST | `/api/batch/disconnect-all` | Déconnecte tout |
| POST | `/api/batch/boot-all` | Boot toutes les sessions |
| POST | `/api/batch/start-all` | Démarre toutes les charges |
| POST | `/api/batch/stop-all` | Arrête toutes les charges |

#### Performance

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/performance/metrics` | Métriques globales |
| POST | `/api/performance/start-load-test` | Lance un test de charge |
| POST | `/api/performance/stop-load-test` | Arrête le test |
| GET | `/api/performance/load-test-status` | État du test |

#### TNR (Tests Non-Régressifs)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/tnr/scenarios` | Liste les scénarios |
| POST | `/api/tnr/scenarios` | Crée un scénario |
| POST | `/api/tnr/scenarios/{id}/run` | Exécute un scénario |
| GET | `/api/tnr/results` | Récupère les résultats |
| POST | `/api/tnr/export/xray` | Export Jira Xray |

#### Health

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/health` | État de santé |
| GET | `/api/info` | Informations application |
| GET | `/api/ready` | Readiness probe |

## WebSocket STOMP

### Connexion

```javascript
const socket = new SockJS('http://localhost:8081/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, (frame) => {
    console.log('Connected: ' + frame);
});
```

### Topics disponibles

| Topic | Description |
|-------|-------------|
| `/topic/sessions/{id}` | Mises à jour d'une session |
| `/topic/metrics` | Métriques temps réel |
| `/topic/logs` | Logs globaux |

### WebSocket natif

Pour les connexions sans STOMP :

```javascript
const ws = new WebSocket('ws://localhost:8081/ws-native');
```

## Messages OCPP 1.6 supportés

### Du Charge Point vers le CSMS (sortant)

- BootNotification
- Authorize
- StartTransaction
- StopTransaction
- MeterValues
- StatusNotification
- Heartbeat
- DiagnosticsStatusNotification
- FirmwareStatusNotification
- DataTransfer

### Du CSMS vers le Charge Point (entrant)

- RemoteStartTransaction
- RemoteStopTransaction
- SetChargingProfile
- ClearChargingProfile
- GetCompositeSchedule
- GetConfiguration
- ChangeConfiguration
- ChangeAvailability
- Reset
- UnlockConnector
- TriggerMessage
- DataTransfer

## Tests

### Lancer les tests unitaires

```bash
mvn test
```

### Lancer les tests avec couverture

```bash
mvn test jacoco:report
```

Le rapport est généré dans `target/site/jacoco/index.html`.

## Architecture

```
src/main/java/com/evse/simulator/
├── config/           # Configurations Spring
│   ├── AsyncConfig.java
│   ├── CorsConfig.java
│   ├── JacksonConfig.java
│   ├── OpenApiConfig.java
│   └── WebSocketConfig.java
├── controller/       # Contrôleurs REST
│   ├── SessionController.java
│   ├── VehicleController.java
│   ├── SmartChargingController.java
│   ├── PerformanceController.java
│   ├── TNRController.java
│   ├── BatchController.java
│   └── HealthController.java
├── exception/        # Gestion des erreurs
│   ├── GlobalExceptionHandler.java
│   ├── SessionNotFoundException.java
│   ├── VehicleNotFoundException.java
│   └── OCPPException.java
├── model/            # Modèles de données
│   ├── Session.java
│   ├── VehicleProfile.java
│   ├── ChargingProfile.java
│   ├── OCPPMessage.java
│   ├── LogEntry.java
│   ├── ChartPoint.java
│   ├── PerformanceMetrics.java
│   └── TNRScenario.java
├── model/enums/      # Énumérations
│   ├── SessionState.java
│   ├── ChargerType.java
│   ├── OCPPAction.java
│   ├── OCPPMessageType.java
│   └── ConnectorStatus.java
├── repository/       # Persistance
│   └── JsonFileRepository.java
├── service/          # Services métier
│   ├── SessionService.java
│   ├── VehicleService.java
│   ├── OCPPService.java
│   ├── SmartChargingService.java
│   ├── PerformanceService.java
│   ├── TNRService.java
│   └── WebSocketBroadcaster.java
└── websocket/        # WebSocket OCPP
    └── OCPPWebSocketClient.java
```

## Optimisations performance

### JVM recommandée pour 25k+ connexions

```bash
java -Xmx8g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+UseStringDeduplication \
     -Dio.netty.leakDetection.level=disabled \
     -jar target/evse-simulator-1.0.0.jar \
     --spring.profiles.active=prod
```

### Configuration Tomcat

Le backend est configuré pour supporter jusqu'à 30 000 connexions simultanées en dev et 50 000 en production grâce aux paramètres dans `application.yml`.

## Dépannage

### Port déjà utilisé

```bash
# Windows
netstat -ano | findstr :8081
taskkill /PID <PID> /F

# Linux/Mac
lsof -i :8081
kill -9 <PID>
```

### Mémoire insuffisante

Augmentez la mémoire JVM :

```bash
java -Xmx4g -Xms2g -jar target/evse-simulator-1.0.0.jar
```

### Logs détaillés

Activez les logs DEBUG dans `application.yml` :

```yaml
logging:
  level:
    com.evse.simulator: DEBUG
    org.java_websocket: DEBUG
```

## Licence

Propriétaire - Usage interne uniquement.