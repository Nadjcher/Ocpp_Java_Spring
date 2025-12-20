package com.evse.simulator.ocpi.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OCPISession {
    private String countryCode;
    private String partyId;
    private String id;
    private Instant startDatetime;
    private Instant endDatetime;
    private BigDecimal kwh;
    private CdrToken cdrToken;
    private AuthMethod authMethod;
    private String locationId;
    private String evseUid;
    private String connectorId;
    private String currency;
    private SessionStatus status;
    private Instant lastUpdated;

    public enum SessionStatus {
        ACTIVE, COMPLETED, INVALID, PENDING, RESERVATION
    }

    public enum AuthMethod {
        AUTH_REQUEST, COMMAND, WHITELIST
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CdrToken {
        private String countryCode;
        private String partyId;
        private String uid;
        private TokenType type;
        private String contractId;
    }

    public enum TokenType {
        AD_HOC_USER, APP_USER, OTHER, RFID
    }
}
