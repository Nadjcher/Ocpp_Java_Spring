package com.evse.simulator.ocpi.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tariff {
    private String countryCode;
    private String partyId;
    private String id;
    private String currency;
    private TariffType type;
    private List<TariffElement> elements;
    private Instant lastUpdated;

    public enum TariffType {
        AD_HOC_PAYMENT, PROFILE_CHEAP, PROFILE_FAST, PROFILE_GREEN, REGULAR
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TariffElement {
        private List<PriceComponent> priceComponents;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceComponent {
        private TariffDimensionType type;
        private BigDecimal price;
        private int stepSize;
    }

    public enum TariffDimensionType {
        ENERGY, FLAT, PARKING_TIME, TIME
    }
}
