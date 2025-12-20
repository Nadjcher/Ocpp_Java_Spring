package com.evse.simulator.tnr.model;

import lombok.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration pour l'exécution TNR.
 * <p>
 * Inclut les paramètres de timing, les tolérances de validation,
 * et les options de comportement lors de l'exécution.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrConfig {

    /** Mode d'exécution (REPLAY, VALIDATION, RECORDING) */
    private String mode;

    /** Arrêter l'exécution si une erreur survient */
    @Builder.Default
    private boolean stopOnError = false;

    /** Continuer l'exécution même si une étape échoue */
    @Builder.Default
    private boolean continueOnError = false;

    /** Capturer les réponses OCPP */
    @Builder.Default
    private boolean captureResponses = true;

    /** Timeout global en millisecondes */
    @Builder.Default
    private int timeoutMs = 300000; // 5 minutes

    /** Nombre de tentatives en cas d'échec */
    @Builder.Default
    private int retryCount = 0;

    /** Délai entre les étapes en millisecondes */
    @Builder.Default
    private long delayBetweenStepsMs = 0;

    /** Facteur d'échelle temporelle (1 = temps réel, 10 = 10x plus rapide) */
    @Builder.Default
    private double timeScale = 1.0;

    /** Durée maximale d'exécution en secondes */
    @Builder.Default
    private int maxDurationSec = 600;

    /** Simuler les réponses CSMS */
    @Builder.Default
    private boolean csmsSimulation = true;

    /** URL du CSMS réel (si csmsSimulation = false) */
    private String csmsUrl;

    /** Tolérances pour la validation */
    @Builder.Default
    private TnrTolerances tolerances = TnrTolerances.defaults();

    /** Champs à ignorer lors de la comparaison */
    @Builder.Default
    private List<String> ignoredFields = new ArrayList<>();

    /** Variables de substitution */
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    /** Profils de charge à injecter */
    @Builder.Default
    private List<Map<String, Object>> injectChargingProfiles = new ArrayList<>();

    /**
     * Retourne une configuration par défaut.
     */
    public static TnrConfig defaults() {
        return TnrConfig.builder().build();
    }

    /**
     * Retourne une configuration pour les tests rapides.
     */
    public static TnrConfig fastMode() {
        return TnrConfig.builder()
                .timeScale(10.0)
                .maxDurationSec(60)
                .tolerances(TnrTolerances.relaxed())
                .continueOnError(true)
                .build();
    }

    /**
     * Retourne une configuration pour les tests stricts.
     */
    public static TnrConfig strictMode() {
        return TnrConfig.builder()
                .timeScale(1.0)
                .tolerances(TnrTolerances.strict())
                .stopOnError(true)
                .continueOnError(false)
                .build();
    }

    /**
     * Calcule le délai ajusté selon le timeScale.
     */
    public long adjustDelay(long originalDelayMs) {
        if (timeScale <= 0) {
            return originalDelayMs;
        }
        return (long) (originalDelayMs / timeScale);
    }
}
