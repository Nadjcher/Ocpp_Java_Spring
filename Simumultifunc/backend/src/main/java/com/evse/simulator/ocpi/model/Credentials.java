package com.evse.simulator.ocpi.model;

import com.evse.simulator.ocpi.OCPIRole;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Credentials {
    private String token;
    private String url;
    private List<CredentialsRole> roles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CredentialsRole {
        private OCPIRole role;
        private BusinessDetails businessDetails;
        private String partyId;
        private String countryCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessDetails {
        private String name;
        private String website;
        private Image logo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Image {
        private String url;
        private String thumbnail;
        private String category;
        private String type;
        private Integer width;
        private Integer height;
    }
}
