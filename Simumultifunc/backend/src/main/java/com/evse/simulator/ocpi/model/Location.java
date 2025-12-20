package com.evse.simulator.ocpi.model;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Location {
    private String countryCode;
    private String partyId;
    private String id;
    private boolean publish;
    private String name;
    private String address;
    private String city;
    private String postalCode;
    private String country;
    private GeoLocation coordinates;
    private List<EVSE> evses;
    private List<DisplayText> directions;
    private Instant lastUpdated;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocation {
        private String latitude;
        private String longitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisplayText {
        private String language;
        private String text;
    }
}
