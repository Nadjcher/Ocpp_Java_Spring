package com.evse.simulator.scheduler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Configuration d'une action planifiée.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionConfig {

    // ═══════════════════════════════════════════════════════════════════
    // SESSION ACTIONS
    // ═══════════════════════════════════════════════════════════════════

    /** Action: "create", "start", "stop", "delete", "plug", "unplug" */
    private String sessionAction;
    private String sessionId;

    /** WebSocket URL OCPP (ex: wss://evse-test.total-ev-charge.com/ocpp/WebSocket) */
    private String wsUrl;

    /** URL legacy - alias de wsUrl pour compatibilité */
    private String url;

    /** Titre de la session */
    private String title;

    /** Charge Point ID */
    private String cpId;

    /** Connecteur (1 par défaut) */
    private Integer connectorId;

    /** ID du profil véhicule */
    private String vehicleId;

    /** Type de chargeur: AC_MONO, AC_TRI, DC */
    private String chargerType;

    /** Type de phase: AC_MONO, AC_TRI, DC */
    private String phaseType;

    /** SoC initial (0-100) */
    private Integer initialSoc;

    /** SoC cible (0-100) */
    private Integer targetSoc;

    /** Badge RFID / ID Tag */
    private String idTag;

    /** Token Bearer pour authentification */
    private String bearerToken;

    /** Puissance max en kW */
    private Double maxPowerKw;

    /** Courant max en A */
    private Double maxCurrentA;

    /** Intervalle heartbeat en secondes */
    private Integer heartbeatInterval;

    /** Intervalle MeterValues en secondes */
    private Integer meterValuesInterval;

    /** Vendor du Charge Point */
    private String vendor;

    /** Modèle du Charge Point */
    private String model;

    /** Numéro de série */
    private String serialNumber;

    /** Version firmware */
    private String firmwareVersion;

    /** Version OCPP: "1.6" ou "2.0.1" */
    private String ocppVersion;

    // ═══════════════════════════════════════════════════════════════════
    // TNR SCENARIOS
    // ═══════════════════════════════════════════════════════════════════

    /** ID du scénario à exécuter */
    private String scenarioId;

    /** URL WebSocket pour TNR (peut override celle du scénario) */
    private String tnrWsUrl;

    /** Paramètres de scénario */
    private Map<String, String> scenarioParams;

    /** Variables du contexte TNR */
    private Map<String, Object> tnrVariables;

    /** Nombre de répétitions */
    private Integer repeatCount;

    /** Continuer en cas d'erreur */
    private Boolean continueOnError;

    /** Timeout global en ms */
    private Integer globalTimeoutMs;

    /** Générer les rapports */
    private Boolean generateReports;

    /** Formats de rapport: json, html, junit */
    private String[] reportFormats;

    // ═══════════════════════════════════════════════════════════════════
    // OCPI TESTS
    // ═══════════════════════════════════════════════════════════════════

    private String partnerId;
    private String testId;
    private String environment;

    // ═══════════════════════════════════════════════════════════════════
    // HTTP REQUESTS
    // ═══════════════════════════════════════════════════════════════════

    private String httpMethod;
    private String httpUrl;
    private Map<String, String> httpHeaders;
    private String httpBody;

    // ═══════════════════════════════════════════════════════════════════
    // COMMON OPTIONS
    // ═══════════════════════════════════════════════════════════════════

    private Integer timeoutSeconds;
    private Integer retryCount;
    private Integer retryDelaySeconds;

    /**
     * Retourne l'URL WebSocket effective (wsUrl prioritaire, sinon url).
     */
    public String getEffectiveWsUrl() {
        return wsUrl != null && !wsUrl.isBlank() ? wsUrl : url;
    }
}
