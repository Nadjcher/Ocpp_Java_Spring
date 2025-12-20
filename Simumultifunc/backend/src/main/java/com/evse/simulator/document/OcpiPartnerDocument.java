package com.evse.simulator.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Document MongoDB pour les partenaires OCPI.
 */
@Document(collection = "ocpiPartners")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcpiPartnerDocument {

    @Id
    private String id;

    @Indexed
    private String name;

    private String description;

    // =========================================================================
    // Partner identification
    // =========================================================================

    private String countryCode;
    private String partyId;

    // =========================================================================
    // Connection details
    // =========================================================================

    private String baseUrl;
    private String versionsUrl;

    @Builder.Default
    private String ocpiVersion = "2.2.1";

    // =========================================================================
    // Authentication
    // =========================================================================

    private String tokenA;
    private String tokenB;
    private String tokenC;

    // =========================================================================
    // Supported modules
    // =========================================================================

    private List<String> supportedModules;
    private Map<String, String> moduleEndpoints;

    // =========================================================================
    // Status
    // =========================================================================

    @Indexed
    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private String status = "PENDING";

    private LocalDateTime lastHandshakeAt;
    private String lastHandshakeStatus;
    private String lastHandshakeError;

    // =========================================================================
    // Registration data
    // =========================================================================

    private LocalDateTime registeredAt;
    private String registrationStatus;

    // =========================================================================
    // Metadata
    // =========================================================================

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
