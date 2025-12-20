package com.evse.simulator.ocpi.service;

import com.evse.simulator.ocpi.OCPIModule;
import com.evse.simulator.ocpi.model.Partner;
import com.evse.simulator.ocpi.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing OCPI partners.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerService {

    private final PartnerRepository partnerRepository;

    /**
     * Get all partners.
     */
    public List<Partner> getAllPartners() {
        return partnerRepository.findAll();
    }

    /**
     * Get active partners only.
     */
    public List<Partner> getActivePartners() {
        return partnerRepository.findActive();
    }

    /**
     * Get partner by ID.
     */
    public Optional<Partner> getPartner(String id) {
        return partnerRepository.findById(id);
    }

    /**
     * Get partners by role (CPO, MSP, HUB).
     */
    public List<Partner> getPartnersByRole(String role) {
        return partnerRepository.findByRole(role);
    }

    /**
     * Create or update partner.
     */
    public Partner savePartner(Partner partner) {
        return partnerRepository.save(partner);
    }

    /**
     * Delete partner.
     */
    public void deletePartner(String id) {
        partnerRepository.delete(id);
    }

    /**
     * Switch partner's active environment.
     */
    public Partner switchEnvironment(String partnerId, String environment) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new IllegalArgumentException("Partner not found: " + partnerId));

        if (partner.getEnvironments() == null || !partner.getEnvironments().containsKey(environment)) {
            throw new IllegalArgumentException("Environment not found: " + environment);
        }

        partner.setActiveEnvironment(environment);
        return partnerRepository.save(partner);
    }

    /**
     * Update partner endpoints after discovery.
     */
    public void updateEndpoints(String partnerId, Map<String, String> endpoints) {
        partnerRepository.updateEndpoints(partnerId, endpoints);
    }

    /**
     * Mark partner as synced.
     */
    public void markSynced(String partnerId) {
        partnerRepository.findById(partnerId).ifPresent(partner -> {
            partner.setLastSync(Instant.now());
            partnerRepository.save(partner);
        });
    }

    /**
     * Get partner's endpoint URL for a module.
     */
    public String getEndpointUrl(String partnerId, OCPIModule module) {
        return partnerRepository.findById(partnerId)
                .map(p -> p.getEndpoint(module))
                .orElse(null);
    }

    /**
     * Check if partner supports a module.
     */
    public boolean supportsModule(String partnerId, OCPIModule module) {
        return partnerRepository.findById(partnerId)
                .map(p -> p.supportsModule(module))
                .orElse(false);
    }

    /**
     * Get partner's active configuration.
     */
    public Partner.EnvironmentConfig getActiveConfig(String partnerId) {
        return partnerRepository.findById(partnerId)
                .map(Partner::getActiveConfig)
                .orElse(null);
    }

    /**
     * Reload partner configurations from disk.
     */
    public void reloadConfigurations() {
        partnerRepository.reload();
        log.info("Reloaded partner configurations");
    }

    /**
     * Validate partner configuration is complete.
     */
    public List<String> validatePartner(String partnerId) {
        Partner partner = partnerRepository.findById(partnerId).orElse(null);
        if (partner == null) {
            return List.of("Partner not found");
        }

        List<String> errors = new java.util.ArrayList<>();

        if (partner.getActiveEnvironment() == null) {
            errors.add("No active environment configured");
        }

        Partner.EnvironmentConfig config = partner.getActiveConfig();
        if (config == null) {
            errors.add("Active environment configuration missing");
        } else {
            if (config.getBaseUrl() == null && config.getVersionsUrl() == null) {
                errors.add("No base URL or versions URL configured");
            }
            if (config.getTokenB() == null) {
                errors.add("Token B (outgoing) not configured");
            }
        }

        return errors;
    }
}
