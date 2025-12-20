package com.evse.simulator.ocpi.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * OCPI Commands module objects.
 * Represents commands that can be sent to EVSEs.
 * This class only contains static inner classes for different command types.
 */
public class Command {

    /**
     * StartSession command.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StartSession {
        private String responseUrl;
        private Token token;
        private String locationId;
        private String evseUid;
        private String connectorId;
        private String authorizationReference;
    }

    /**
     * StopSession command.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StopSession {
        private String responseUrl;
        private String sessionId;
    }

    /**
     * ReserveNow command.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReserveNow {
        private String responseUrl;
        private Token token;
        private Instant expiryDate;
        private String reservationId;
        private String locationId;
        private String evseUid;
        private String connectorId;
        private String authorizationReference;
    }

    /**
     * CancelReservation command.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelReservation {
        private String responseUrl;
        private String reservationId;
    }

    /**
     * UnlockConnector command.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnlockConnector {
        private String responseUrl;
        private String locationId;
        private String evseUid;
        private String connectorId;
    }

    /**
     * SetChargingProfile command.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetChargingProfile {
        private String responseUrl;
        private ChargingProfile chargingProfile;
    }

    /**
     * Command response from CPO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandResponse {
        private CommandResponseType result;
        private Integer timeout;
        private Location.DisplayText message;
    }

    /**
     * Async command result sent to response_url.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandResult {
        private CommandResultType result;
        private Location.DisplayText message;
    }

    /**
     * Command response types.
     */
    public enum CommandResponseType {
        NOT_SUPPORTED, REJECTED, ACCEPTED, UNKNOWN_SESSION
    }

    /**
     * Command result types.
     */
    public enum CommandResultType {
        ACCEPTED, CANCELED_RESERVATION, EVSE_OCCUPIED, EVSE_INOPERATIVE,
        FAILED, NOT_SUPPORTED, REJECTED, TIMEOUT, UNKNOWN_RESERVATION
    }
}
