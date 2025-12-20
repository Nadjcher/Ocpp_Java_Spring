package com.evse.simulator.tnr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Résultat d'exécution d'un step TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrStepResult {

    /**
     * Statut d'exécution du step.
     */
    public enum Status {
        PASSED,      // Step réussi
        FAILED,      // Assertion échouée
        SKIPPED,     // Non exécuté (step précédent échoué)
        ERROR,       // Erreur technique
        PENDING,     // En attente
        UNDEFINED    // Step non défini (pas de handler)
    }

    /** Step exécuté */
    private TnrStep step;

    /** Statut */
    @Builder.Default
    private Status status = Status.PENDING;

    /** Heure de début */
    private Instant startTime;

    /** Heure de fin */
    private Instant endTime;

    /** Durée en ms */
    private long durationMs;

    /** Message d'erreur */
    private String errorMessage;

    /** Stack trace */
    private String stackTrace;

    /** Assertions effectuées */
    @Builder.Default
    private List<TnrAssertion> assertions = new ArrayList<>();

    /** Données capturées pendant l'exécution */
    @Builder.Default
    private Map<String, Object> capturedData = new HashMap<>();

    /** Messages OCPP échangés */
    @Builder.Default
    private List<OcppMessageCapture> ocppMessages = new ArrayList<>();

    /** Logs générés */
    @Builder.Default
    private List<String> logs = new ArrayList<>();

    /** Pièces jointes (screenshots, etc.) */
    @Builder.Default
    private List<TnrAttachment> attachments = new ArrayList<>();

    /**
     * Vérifie si le step a réussi.
     */
    public boolean isSuccess() {
        return status == Status.PASSED;
    }

    /**
     * Ajoute une assertion.
     */
    public void addAssertion(TnrAssertion assertion) {
        if (assertions == null) assertions = new ArrayList<>();
        assertions.add(assertion);
    }

    /**
     * Ajoute un message OCPP capturé.
     */
    public void addOcppMessage(OcppMessageCapture message) {
        if (ocppMessages == null) ocppMessages = new ArrayList<>();
        ocppMessages.add(message);
    }

    /**
     * Ajoute un log.
     */
    public void addLog(String log) {
        if (logs == null) logs = new ArrayList<>();
        logs.add(log);
    }

    /**
     * Compte les assertions réussies.
     */
    public long getPassedAssertionCount() {
        if (assertions == null) return 0;
        return assertions.stream().filter(TnrAssertion::isPassed).count();
    }

    /**
     * Compte les assertions échouées.
     */
    public long getFailedAssertionCount() {
        if (assertions == null) return 0;
        return assertions.stream().filter(a -> !a.isPassed()).count();
    }
}
