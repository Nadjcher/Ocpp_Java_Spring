package com.evse.simulator.ocpi.model;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Token {
    private String countryCode;
    private String partyId;
    private String uid;
    private TokenType type;
    private String contractId;
    private String visualNumber;
    private String issuer;
    private String groupId;
    private boolean valid;
    private WhitelistType whitelist;
    private String language;
    private Instant lastUpdated;

    public enum TokenType {
        AD_HOC_USER, APP_USER, OTHER, RFID
    }

    public enum WhitelistType {
        ALWAYS, ALLOWED, ALLOWED_OFFLINE, NEVER
    }
}
