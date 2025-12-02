package com.evse.simulator.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Actions OCPP 1.6 supportées.
 * <p>
 * Définit toutes les opérations du protocole OCPP 1.6.
 * </p>
 */
public enum OCPPAction {

    // =========================================================================
    // Charge Point → Central System (Initiated by CP)
    // =========================================================================

    /**
     * Notification de démarrage du point de charge.
     */
    BOOT_NOTIFICATION("BootNotification", Direction.CP_TO_CS),

    /**
     * Demande d'autorisation pour un badge/token.
     */
    AUTHORIZE("Authorize", Direction.CP_TO_CS),

    /**
     * Heartbeat périodique.
     */
    HEARTBEAT("Heartbeat", Direction.CP_TO_CS),

    /**
     * Notification de changement d'état du connecteur.
     */
    STATUS_NOTIFICATION("StatusNotification", Direction.CP_TO_CS),

    /**
     * Démarrage d'une transaction de charge.
     */
    START_TRANSACTION("StartTransaction", Direction.CP_TO_CS),

    /**
     * Arrêt d'une transaction de charge.
     */
    STOP_TRANSACTION("StopTransaction", Direction.CP_TO_CS),

    /**
     * Envoi de valeurs de compteur.
     */
    METER_VALUES("MeterValues", Direction.CP_TO_CS),

    /**
     * Transfert de données propriétaires.
     */
    DATA_TRANSFER("DataTransfer", Direction.BIDIRECTIONAL),

    /**
     * Notification de diagnostic.
     */
    DIAGNOSTICS_STATUS_NOTIFICATION("DiagnosticsStatusNotification", Direction.CP_TO_CS),

    /**
     * Notification de mise à jour firmware.
     */
    FIRMWARE_STATUS_NOTIFICATION("FirmwareStatusNotification", Direction.CP_TO_CS),

    // =========================================================================
    // Central System → Charge Point (Initiated by CS)
    // =========================================================================

    /**
     * Démarrage à distance d'une transaction.
     */
    REMOTE_START_TRANSACTION("RemoteStartTransaction", Direction.CS_TO_CP),

    /**
     * Arrêt à distance d'une transaction.
     */
    REMOTE_STOP_TRANSACTION("RemoteStopTransaction", Direction.CS_TO_CP),

    /**
     * Définition d'un profil de charge.
     */
    SET_CHARGING_PROFILE("SetChargingProfile", Direction.CS_TO_CP),

    /**
     * Suppression d'un profil de charge.
     */
    CLEAR_CHARGING_PROFILE("ClearChargingProfile", Direction.CS_TO_CP),

    /**
     * Récupération du planning de charge composite.
     */
    GET_COMPOSITE_SCHEDULE("GetCompositeSchedule", Direction.CS_TO_CP),

    /**
     * Récupération de la configuration.
     */
    GET_CONFIGURATION("GetConfiguration", Direction.CS_TO_CP),

    /**
     * Modification de la configuration.
     */
    CHANGE_CONFIGURATION("ChangeConfiguration", Direction.CS_TO_CP),

    /**
     * Changement de disponibilité du connecteur.
     */
    CHANGE_AVAILABILITY("ChangeAvailability", Direction.CS_TO_CP),

    /**
     * Réinitialisation du point de charge.
     */
    RESET("Reset", Direction.CS_TO_CP),

    /**
     * Déverrouillage du connecteur.
     */
    UNLOCK_CONNECTOR("UnlockConnector", Direction.CS_TO_CP),

    /**
     * Réservation d'un connecteur.
     */
    RESERVE_NOW("ReserveNow", Direction.CS_TO_CP),

    /**
     * Annulation de réservation.
     */
    CANCEL_RESERVATION("CancelReservation", Direction.CS_TO_CP),

    /**
     * Déclenchement d'un message.
     */
    TRIGGER_MESSAGE("TriggerMessage", Direction.CS_TO_CP),

    /**
     * Mise à jour de la liste d'autorisation locale.
     */
    SEND_LOCAL_LIST("SendLocalList", Direction.CS_TO_CP),

    /**
     * Récupération de la liste d'autorisation locale.
     */
    GET_LOCAL_LIST_VERSION("GetLocalListVersion", Direction.CS_TO_CP),

    /**
     * Récupération des diagnostics.
     */
    GET_DIAGNOSTICS("GetDiagnostics", Direction.CS_TO_CP),

    /**
     * Mise à jour du firmware.
     */
    UPDATE_FIRMWARE("UpdateFirmware", Direction.CS_TO_CP),

    /**
     * Effacement du cache.
     */
    CLEAR_CACHE("ClearCache", Direction.CS_TO_CP);

    private final String value;
    private final Direction direction;

    OCPPAction(String value, Direction direction) {
        this.value = value;
        this.direction = direction;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Direction du message.
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * Vérifie si l'action est initiée par le Charge Point.
     */
    public boolean isFromChargePoint() {
        return direction == Direction.CP_TO_CS || direction == Direction.BIDIRECTIONAL;
    }

    /**
     * Vérifie si l'action est initiée par le Central System.
     */
    public boolean isFromCentralSystem() {
        return direction == Direction.CS_TO_CP || direction == Direction.BIDIRECTIONAL;
    }

    /**
     * Convertit une chaîne en OCPPAction.
     */
    public static OCPPAction fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (OCPPAction action : values()) {
            if (action.value.equalsIgnoreCase(value) || action.name().equalsIgnoreCase(value)) {
                return action;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Direction d'un message OCPP.
     */
    public enum Direction {
        /**
         * Du Charge Point vers le Central System.
         */
        CP_TO_CS,

        /**
         * Du Central System vers le Charge Point.
         */
        CS_TO_CP,

        /**
         * Bidirectionnel.
         */
        BIDIRECTIONAL
    }
}