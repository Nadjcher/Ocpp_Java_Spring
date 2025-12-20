package com.evse.simulator.model;

import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.model.enums.SessionState;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Représente une session de charge EVSE complète.
 * <p>
 * Contient toutes les informations nécessaires pour simuler
 * une borne de recharge et sa communication OCPP.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(hidden = true)
public class Session {

    /**
     * Identifiant unique de la session.
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * Titre/nom de la session pour l'affichage.
     */
    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    /**
     * URL du CSMS (Central System Management System).
     */
    @NotBlank(message = "L'URL est obligatoire")
    private String url;

    /**
     * Identifiant du Charge Point.
     */
    @NotBlank(message = "L'ID du Charge Point est obligatoire")
    private String cpId;

    /**
     * Token d'authentification Bearer (optionnel).
     */
    private String bearerToken;

    /**
     * État actuel de la session.
     */
    @Builder.Default
    private SessionState state = SessionState.DISCONNECTED;

    /**
     * Timestamp du dernier changement d'état.
     */
    private LocalDateTime lastStateChange;

    /**
     * Identifiant du profil véhicule utilisé.
     */
    private String vehicleProfile;

    /**
     * Type de chargeur.
     */
    @Builder.Default
    private ChargerType chargerType = ChargerType.AC_TRI;

    /**
     * Identifiant du connecteur (1 par défaut).
     */
    @Builder.Default
    @Min(1)
    @Max(10)
    private int connectorId = 1;

    /**
     * Badge RFID ou identifiant utilisateur.
     */
    @Builder.Default
    private String idTag = "EVSE001";

    /**
     * Identifiant de la transaction active.
     */
    private String transactionId;

    /**
     * Identifiant de la réservation active.
     */
    private Integer reservationId;

    /**
     * Date d'expiration de la réservation.
     */
    private LocalDateTime reservationExpiry;

    // =========================================================================
    // État de charge
    // =========================================================================

    /**
     * State of Charge actuel (0-100%).
     */
    @Builder.Default
    @Min(0)
    @Max(100)
    private double soc = 20.0;

    /**
     * State of Charge cible.
     */
    @Builder.Default
    @Min(0)
    @Max(100)
    private double targetSoc = 80.0;

    /**
     * Puissance de charge actuelle en kW.
     */
    @Builder.Default
    @Min(0)
    private double currentPowerKw = 0.0;

    /**
     * Puissance maximale de l'EVSE en kW.
     */
    @Builder.Default
    @Min(0)
    private double maxPowerKw = 22.0;

    /**
     * Courant maximum en Ampères.
     */
    @Builder.Default
    @Min(0)
    private double maxCurrentA = 32.0;

    /**
     * Énergie totale délivrée en kWh.
     */
    @Builder.Default
    @Min(0)
    private double energyDeliveredKwh = 0.0;

    /**
     * Valeur du compteur en Wh.
     */
    @Builder.Default
    @Min(0)
    private int meterValue = 0;

    /**
     * Tension actuelle en Volts.
     */
    @Builder.Default
    private double voltage = 230.0;

    /**
     * Courant actuel en Ampères.
     */
    @Builder.Default
    private double currentA = 0.0;

    /**
     * Nombre de phases actives pour la charge.
     * Peut être inférieur à chargerType.getPhases() si le véhicule
     * ou le test de phasage limite le nombre de phases.
     * Par défaut, utilise les phases du chargerType.
     */
    private Integer activePhases;

    /**
     * Température de la batterie en °C.
     */
    @Builder.Default
    private double temperature = 25.0;

    // =========================================================================
    // Smart Charging Profile (SCP)
    // =========================================================================

    /**
     * Limite de puissance SCP active en kW.
     */
    @Builder.Default
    private double scpLimitKw = 0.0;

    /**
     * Limite de courant SCP active en A.
     */
    @Builder.Default
    private double scpLimitA = 0.0;

    /**
     * ID du profil de charge actif.
     */
    private Integer scpProfileId;

    /**
     * Purpose du profil actif (ChargePointMaxProfile, TxDefaultProfile, TxProfile).
     */
    private String scpPurpose;

    /**
     * Stack level du profil actif.
     */
    @Builder.Default
    private int scpStackLevel = 0;

    /**
     * Secondes avant le prochain changement de période.
     */
    private Integer scpNextPeriodSeconds;

    /**
     * Limite de la prochaine période en kW.
     */
    private Double scpNextLimitKw;

    /**
     * Type de phase (AC_MONO, AC_TRI, DC).
     */
    @Builder.Default
    private String phaseType = "AC_TRI";

    // =========================================================================
    // État de connexion
    // =========================================================================

    /**
     * WebSocket connecté au CSMS.
     */
    @Builder.Default
    private boolean connected = false;

    /**
     * Autorisation acceptée.
     */
    @Builder.Default
    private boolean authorized = false;

    /**
     * Charge en cours.
     */
    @Builder.Default
    private boolean charging = false;

    /**
     * Véhicule garé (configuré).
     */
    @Builder.Default
    private boolean parked = false;

    /**
     * Câble branché.
     */
    @Builder.Default
    private boolean plugged = false;

    /**
     * Heartbeat actif.
     */
    @Builder.Default
    private boolean heartbeatActive = false;

    /**
     * MeterValues automatiques actifs.
     */
    @Builder.Default
    private boolean meterValuesActive = false;

    /**
     * Flag indiquant si la déconnexion est volontaire (bouton Stop).
     * Si false, la session peut être reconnectée automatiquement.
     */
    @Builder.Default
    private boolean voluntaryStop = false;

    /**
     * Flag indiquant si la session est en arrière-plan (onglet caché).
     */
    @Builder.Default
    private boolean backgrounded = false;

    /**
     * Timestamp du dernier keepalive reçu.
     */
    private LocalDateTime lastKeepalive;

    /**
     * Nombre de tentatives de reconnexion.
     */
    @Builder.Default
    private int reconnectAttempts = 0;

    /**
     * Raison de la dernière déconnexion.
     */
    private String disconnectReason;

    /**
     * Statut du BootNotification (Accepted, Pending, Rejected).
     * Null si pas encore de réponse reçue.
     */
    private String bootStatus;

    // =========================================================================
    // Configuration OCPP
    // =========================================================================

    /**
     * Intervalle de heartbeat en secondes.
     */
    @Builder.Default
    private int heartbeatInterval = 30;

    /**
     * Intervalle de MeterValues en secondes.
     */
    @Builder.Default
    private int meterValuesInterval = 10;

    /**
     * Vendor du Charge Point.
     */
    @Builder.Default
    private String vendor = "EVSE Simulator";

    /**
     * Modèle du Charge Point.
     */
    @Builder.Default
    private String model = "SimuCP-1";

    /**
     * Numéro de série.
     */
    private String serialNumber;

    /**
     * Version du firmware.
     */
    @Builder.Default
    private String firmwareVersion = "1.0.0";

    /**
     * Version du protocole OCPP (1.6 ou 2.0.1).
     */
    @Builder.Default
    private String ocppVersion = "1.6";

    // =========================================================================
    // Horodatage
    // =========================================================================

    /**
     * Date de création de la session.
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Date de dernière mise à jour.
     */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * Date de début de transaction.
     */
    private LocalDateTime startTime;

    /**
     * Date de fin de transaction.
     */
    private LocalDateTime stopTime;

    /**
     * Date de dernière connexion.
     */
    private LocalDateTime lastConnected;

    /**
     * Date du dernier heartbeat.
     */
    private LocalDateTime lastHeartbeat;

    // =========================================================================
    // Données temps réel (limité à 500 entrées)
    // =========================================================================

    /**
     * Historique des logs.
     */
    @Builder.Default
    private List<LogEntry> logs = new ArrayList<>();

    /**
     * Données du graphique SoC.
     */
    @Builder.Default
    private List<ChartPoint> socData = new ArrayList<>();

    /**
     * Données du graphique de puissance.
     */
    @Builder.Default
    private List<ChartPoint> powerData = new ArrayList<>();

    /**
     * Historique des messages OCPP.
     */
    @Builder.Default
    private List<OCPPMessage> ocppMessages = new ArrayList<>();

    // =========================================================================
    // Profil de charge actif
    // =========================================================================

    /**
     * Profil de charge actif (Smart Charging).
     */
    private ChargingProfile activeChargingProfile;

    // =========================================================================
    // Méthodes utilitaires
    // =========================================================================

    /**
     * Retourne le nombre de phases actives pour la génération MeterValues.
     * Utilise activePhases si configuré, sinon les phases du chargerType.
     *
     * @return nombre de phases actives (1, 2 ou 3)
     */
    public int getEffectivePhases() {
        if (activePhases != null && activePhases > 0) {
            return activePhases;
        }
        return chargerType != null ? chargerType.getPhases() : 1;
    }

    /**
     * Ajoute un log avec limitation de taille.
     */
    public void addLog(LogEntry log) {
        if (logs == null) {
            logs = new ArrayList<>();
        }
        logs.add(log);
        if (logs.size() > 500) {
            logs = new ArrayList<>(logs.subList(logs.size() - 500, logs.size()));
        }
    }

    /**
     * Ajoute un point de données SoC.
     */
    public void addSocDataPoint(ChartPoint point) {
        if (socData == null) {
            socData = new ArrayList<>();
        }
        socData.add(point);
        if (socData.size() > 500) {
            socData = new ArrayList<>(socData.subList(socData.size() - 500, socData.size()));
        }
    }

    /**
     * Ajoute un point de données puissance.
     */
    public void addPowerDataPoint(ChartPoint point) {
        if (powerData == null) {
            powerData = new ArrayList<>();
        }
        powerData.add(point);
        if (powerData.size() > 500) {
            powerData = new ArrayList<>(powerData.subList(powerData.size() - 500, powerData.size()));
        }
    }

    /**
     * Ajoute un message OCPP.
     */
    public void addOcppMessage(OCPPMessage message) {
        if (ocppMessages == null) {
            ocppMessages = new ArrayList<>();
        }
        ocppMessages.add(message);
        if (ocppMessages.size() > 500) {
            ocppMessages = new ArrayList<>(ocppMessages.subList(ocppMessages.size() - 500, ocppMessages.size()));
        }
    }

    /**
     * Met à jour le timestamp de modification.
     */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Vérifie si la session peut démarrer une charge.
     */
    public boolean canStartCharging() {
        return connected && authorized && !charging &&
                (state == SessionState.AVAILABLE || state == SessionState.PREPARING);
    }

    /**
     * Vérifie si la session peut arrêter une charge.
     */
    public boolean canStopCharging() {
        return connected && charging && transactionId != null;
    }

    /**
     * Calcule la durée de charge en minutes.
     */
    public long getChargingDurationMinutes() {
        if (startTime == null) {
            return 0;
        }
        LocalDateTime end = stopTime != null ? stopTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, end).toMinutes();
    }

    /**
     * Crée un résumé de la session pour le logging.
     */
    public String toSummary() {
        return String.format("Session[%s] %s - %s - SoC: %.1f%% - Power: %.1fkW",
                id.substring(0, 8), cpId, state, soc, currentPowerKw);
    }

    /**
     * Retourne le status de la session pour la compatibilité frontend.
     * Le status est directement la valeur de l'état SessionState.
     */
    @JsonProperty("status")
    public String getStatus() {
        if (state == null) {
            return "closed";
        }
        return state.getValue();
    }

    /**
     * Retourne l'ID de transaction pour la compatibilité frontend.
     */
    @JsonProperty("txId")
    public Integer getTxId() {
        if (transactionId == null || transactionId.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(transactionId);
        } catch (NumberFormatException e) {
            return transactionId.hashCode();
        }
    }

    /**
     * Alias pour isConnected (compatibilité frontend).
     */
    @JsonProperty("isConnected")
    public boolean getIsConnected() {
        return connected;
    }

    /**
     * Alias pour isCharging (compatibilité frontend).
     */
    @JsonProperty("isCharging")
    public boolean getIsCharging() {
        return charging;
    }

    /**
     * Retourne un objet metrics pour la compatibilité frontend SimulGPMTab.
     * Les valeurs sont converties en W et Wh (le frontend divise par 1000).
     */
    @JsonProperty("metrics")
    public java.util.Map<String, Object> getMetrics() {
        java.util.Map<String, Object> metrics = new java.util.HashMap<>();
        // Conversion kW -> W pour activePower
        metrics.put("activePower", currentPowerKw * 1000);
        // Conversion kW -> W pour offeredPower
        metrics.put("offeredPower", maxPowerKw * 1000);
        // SoC en %
        metrics.put("soc", soc);
        // Conversion kWh -> Wh pour energy
        metrics.put("energy", energyDeliveredKwh * 1000);
        // Tension en V
        metrics.put("voltage", voltage);
        // Courant en A
        metrics.put("current", currentA);
        return metrics;
    }

    /**
     * Retourne le meterValue en Wh (compatibilité frontend).
     */
    @JsonProperty("meterWh")
    public int getMeterWh() {
        return meterValue;
    }

    /**
     * Retourne la durée de charge en secondes depuis startTime (compatibilité frontend).
     */
    @JsonProperty("chargingDurationSec")
    public Long getChargingDurationSec() {
        if (startTime == null) {
            return 0L;
        }
        LocalDateTime end = stopTime != null ? stopTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, end).getSeconds();
    }

    /**
     * Retourne le timestamp de début de charge en millisecondes (compatibilité frontend).
     */
    @JsonProperty("chargeStartTime")
    public Long getChargeStartTime() {
        if (startTime == null) {
            return null;
        }
        return startTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Retourne l'URL WebSocket pour l'affichage (compatibilité frontend).
     */
    @JsonProperty("wsUrl")
    public String getWsUrl() {
        return url;
    }

    /**
     * Met à jour le timestamp de keepalive.
     */
    public void updateKeepalive() {
        this.lastKeepalive = LocalDateTime.now();
        this.backgrounded = false;
    }

    /**
     * Vérifie si la session est considérée comme abandonnée (pas de keepalive depuis 5 min).
     */
    public boolean isStale() {
        if (lastKeepalive == null) {
            return false;
        }
        return java.time.Duration.between(lastKeepalive, LocalDateTime.now()).toMinutes() > 5;
    }

    /**
     * Marque la session comme en arrière-plan.
     */
    public void setBackgrounded(boolean backgrounded) {
        this.backgrounded = backgrounded;
        if (!backgrounded) {
            this.lastKeepalive = LocalDateTime.now();
        }
    }

    /**
     * Réinitialise le compteur de reconnexion.
     */
    public void resetReconnectAttempts() {
        this.reconnectAttempts = 0;
    }

    /**
     * Incrémente le compteur de reconnexion.
     */
    public int incrementReconnectAttempts() {
        return ++this.reconnectAttempts;
    }

    /**
     * Vérifie si la session peut être reconnectée.
     */
    public boolean canReconnect() {
        return !voluntaryStop && reconnectAttempts < 10;
    }

    /**
     * Prépare la session pour une déconnexion volontaire.
     */
    public void prepareVoluntaryDisconnect(String reason) {
        this.voluntaryStop = true;
        this.disconnectReason = reason;
        this.touch();
    }

    /**
     * Prépare la session pour une reconnexion.
     */
    public void prepareReconnect() {
        this.voluntaryStop = false;
        this.disconnectReason = null;
        this.backgrounded = false;
        this.touch();
    }
}