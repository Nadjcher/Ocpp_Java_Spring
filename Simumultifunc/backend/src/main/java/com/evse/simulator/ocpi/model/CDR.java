package com.evse.simulator.ocpi.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CDR {
    private String countryCode;
    private String partyId;
    private String id;
    private Instant startDateTime;
    private Instant endDateTime;
    private String sessionId;
    private OCPISession.CdrToken cdrToken;
    private OCPISession.AuthMethod authMethod;
    private String currency;
    private BigDecimal totalCost;
    private BigDecimal totalEnergy;
    private BigDecimal totalTime;
    private Instant lastUpdated;
}
