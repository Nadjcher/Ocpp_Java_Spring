package com.evse.simulator.ocpi.repository;

import com.evse.simulator.ocpi.model.Partner;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository for OCPI Partner configurations.
 * Loads partners from JSON files and manages in-memory cache.
 */
@Repository
@Slf4j
public class PartnerRepository {

    @Value("${ocpi.partners.directory:./data/ocpi/partners}")
    private String partnersDirectory;

    private final ObjectMapper objectMapper;
    private final Map<String, Partner> partnersCache = new ConcurrentHashMap<>();

    public PartnerRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        loadPartnersFromDirectory();
    }

    /**
     * Load all partner configurations from the partners directory.
     */
    public void loadPartnersFromDirectory() {
        File dir = new File(partnersDirectory);
        if (!dir.exists()) {
            log.warn("Partners directory does not exist: {}. Creating it.", partnersDirectory);
            dir.mkdirs();
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            log.info("No partner configuration files found in {}", partnersDirectory);
            return;
        }

        partnersCache.clear();
        for (File file : files) {
            try {
                Partner partner = objectMapper.readValue(file, Partner.class);
                if (partner.getId() == null) {
                    partner.setId(file.getName().replace(".json", ""));
                }
                partnersCache.put(partner.getId(), partner);
                log.info("Loaded partner configuration: {} ({})", partner.getName(), partner.getId());
            } catch (IOException e) {
                log.error("Failed to load partner configuration from {}: {}", file.getName(), e.getMessage());
            }
        }

        log.info("Loaded {} partner configurations", partnersCache.size());
    }

    /**
     * Get all partners.
     */
    public List<Partner> findAll() {
        return new ArrayList<>(partnersCache.values());
    }

    /**
     * Get partner by ID.
     */
    public Optional<Partner> findById(String id) {
        return Optional.ofNullable(partnersCache.get(id));
    }

    /**
     * Get partners by role.
     */
    public List<Partner> findByRole(String role) {
        return partnersCache.values().stream()
                .filter(p -> p.getRole() != null && p.getRole().getValue().equalsIgnoreCase(role))
                .toList();
    }

    /**
     * Get active partners only.
     */
    public List<Partner> findActive() {
        return partnersCache.values().stream()
                .filter(Partner::isActive)
                .toList();
    }

    /**
     * Save partner configuration.
     */
    public Partner save(Partner partner) {
        if (partner.getId() == null) {
            partner.setId(UUID.randomUUID().toString());
        }

        partnersCache.put(partner.getId(), partner);

        // Persist to file
        try {
            File dir = new File(partnersDirectory);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, partner.getId() + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, partner);
            log.info("Saved partner configuration: {}", partner.getId());
        } catch (IOException e) {
            log.error("Failed to save partner configuration: {}", e.getMessage());
        }

        return partner;
    }

    /**
     * Delete partner configuration.
     */
    public void delete(String id) {
        partnersCache.remove(id);

        File file = new File(partnersDirectory, id + ".json");
        if (file.exists()) {
            if (file.delete()) {
                log.info("Deleted partner configuration: {}", id);
            } else {
                log.error("Failed to delete partner file: {}", id);
            }
        }
    }

    /**
     * Update partner endpoints after version discovery.
     */
    public void updateEndpoints(String partnerId, Map<String, String> endpoints) {
        Partner partner = partnersCache.get(partnerId);
        if (partner != null) {
            partner.setEndpoints(endpoints);
            save(partner);
        }
    }

    /**
     * Reload configurations from disk.
     */
    public void reload() {
        loadPartnersFromDirectory();
    }
}
