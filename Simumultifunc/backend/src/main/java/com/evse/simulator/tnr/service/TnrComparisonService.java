package com.evse.simulator.tnr.service;

import com.evse.simulator.tnr.model.*;
import com.evse.simulator.tnr.model.TnrComparisonResult.ComparisonOptions;
import com.evse.simulator.tnr.model.TnrComparisonResult.Verdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Service de comparaison d'exécutions TNR.
 * <p>
 * Compare deux exécutions et détecte les régressions en analysant :
 * - La séquence des événements
 * - Les payloads OCPP
 * - Les latences
 * - Les erreurs
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TnrComparisonService {

    private final TnrSignatureService signatureService;
    private final TnrRecordingService recordingService;

    /**
     * Compare deux exécutions TNR.
     *
     * @param baselineId ID de l'exécution de référence
     * @param comparedId ID de l'exécution à comparer
     * @param options options de comparaison (optionnel)
     * @return résultat de la comparaison
     */
    public TnrComparisonResult compare(String baselineId, String comparedId, ComparisonOptions options) {
        long startTime = System.currentTimeMillis();

        if (options == null) {
            options = ComparisonOptions.defaults();
        }

        // Récupérer les exécutions
        TnrExecution baseline = recordingService.getExecution(baselineId)
                .orElseThrow(() -> new IllegalArgumentException("Baseline execution not found: " + baselineId));

        TnrExecution compared = recordingService.getExecution(comparedId)
                .orElseThrow(() -> new IllegalArgumentException("Compared execution not found: " + comparedId));

        return compareExecutions(baseline, compared, options, startTime);
    }

    /**
     * Compare deux objets TnrExecution directement.
     */
    public TnrComparisonResult compareExecutions(TnrExecution baseline, TnrExecution compared,
                                                   ComparisonOptions options, long startTime) {
        if (options == null) {
            options = ComparisonOptions.defaults();
        }

        // Filtrer les événements selon les options
        List<TnrEvent> baselineEvents = filterEvents(baseline.getEvents(), options);
        List<TnrEvent> comparedEvents = filterEvents(compared.getEvents(), options);

        // Comparer les signatures
        String baselineSig = signatureService.computeSignature(baselineEvents);
        String comparedSig = signatureService.computeSignature(comparedEvents);
        boolean signatureMatch = signatureService.signaturesMatch(baselineSig, comparedSig);

        String baselineCritSig = signatureService.computeCriticalSignature(baselineEvents);
        String comparedCritSig = signatureService.computeCriticalSignature(comparedEvents);
        boolean criticalMatch = signatureService.signaturesMatch(baselineCritSig, comparedCritSig);

        // Détecter les différences
        List<TnrDifference> differences = detectDifferences(baselineEvents, comparedEvents, options);

        // Calculer la similarité
        double similarity = signatureService.computeSimilarity(baselineEvents, comparedEvents);

        // Déterminer le verdict
        Verdict verdict = determineVerdict(signatureMatch, criticalMatch, differences, similarity);

        TnrComparisonResult result = TnrComparisonResult.builder()
                .baselineId(baseline.getId())
                .baselineName(baseline.getScenarioName())
                .comparedId(compared.getId())
                .comparedName(compared.getScenarioName())
                .signatureMatch(signatureMatch)
                .criticalSignatureMatch(criticalMatch)
                .baselineEventCount(baselineEvents.size())
                .comparedEventCount(comparedEvents.size())
                .differences(differences)
                .similarityPercent(similarity)
                .comparedAt(Instant.now())
                .durationMs(System.currentTimeMillis() - startTime)
                .verdict(verdict)
                .options(options)
                .build();

        result.setSummary(result.generateSummary());

        log.info("TNR comparison completed: {} vs {} -> {} ({} differences)",
                baseline.getId(), compared.getId(), verdict, differences.size());

        return result;
    }

    /**
     * Filtre les événements selon les options.
     */
    private List<TnrEvent> filterEvents(List<TnrEvent> events, ComparisonOptions options) {
        if (events == null) return List.of();

        return events.stream()
                .filter(e -> {
                    // Filtrer les types ignorés
                    if (e.getType() != null && options.getIgnoredEventTypes().contains(e.getType())) {
                        return false;
                    }

                    // Filtrer les actions ignorées
                    if (e.getAction() != null && options.getIgnoredActions().contains(e.getAction())) {
                        return false;
                    }

                    // Filtrer les heartbeats si demandé
                    if (options.isIgnoreHeartbeats() && "Heartbeat".equalsIgnoreCase(e.getAction())) {
                        return false;
                    }

                    // Filtrer les événements non critiques si demandé
                    if (options.isCriticalOnly() && !e.isCritical()) {
                        return false;
                    }

                    return true;
                })
                .toList();
    }

    /**
     * Détecte les différences entre deux séquences d'événements.
     */
    private List<TnrDifference> detectDifferences(List<TnrEvent> baseline, List<TnrEvent> compared,
                                                    ComparisonOptions options) {
        List<TnrDifference> differences = new ArrayList<>();

        if (options.isIgnoreOrder()) {
            // Comparaison par ensemble (ignore l'ordre)
            detectSetDifferences(baseline, compared, differences);
        } else {
            // Comparaison séquentielle
            detectSequentialDifferences(baseline, compared, differences, options);
        }

        return differences;
    }

    /**
     * Détection des différences en mode séquentiel.
     */
    private void detectSequentialDifferences(List<TnrEvent> baseline, List<TnrEvent> compared,
                                               List<TnrDifference> differences, ComparisonOptions options) {
        int maxLen = Math.max(baseline.size(), compared.size());

        for (int i = 0; i < maxLen; i++) {
            TnrEvent baseEvent = i < baseline.size() ? baseline.get(i) : null;
            TnrEvent compEvent = i < compared.size() ? compared.get(i) : null;

            if (baseEvent == null) {
                // Événement en plus dans la version comparée
                differences.add(TnrDifference.extra(i, compEvent));
            } else if (compEvent == null) {
                // Événement manquant dans la version comparée
                differences.add(TnrDifference.missing(i, baseEvent));
            } else if (!eventsMatch(baseEvent, compEvent, options)) {
                // Événements différents
                if (!typeAndActionMatch(baseEvent, compEvent)) {
                    differences.add(TnrDifference.typeChanged(i, baseEvent, compEvent));
                } else {
                    // Même type/action mais payload différent
                    differences.add(TnrDifference.modified(i, baseEvent, compEvent, "payload"));
                }
            }
        }
    }

    /**
     * Détection des différences en mode ensemble (ignore l'ordre).
     */
    private void detectSetDifferences(List<TnrEvent> baseline, List<TnrEvent> compared,
                                        List<TnrDifference> differences) {
        // Créer des "clés" pour chaque événement
        Map<String, Integer> baselineCounts = new HashMap<>();
        Map<String, Integer> comparedCounts = new HashMap<>();

        for (TnrEvent e : baseline) {
            String key = eventKey(e);
            baselineCounts.merge(key, 1, Integer::sum);
        }

        for (TnrEvent e : compared) {
            String key = eventKey(e);
            comparedCounts.merge(key, 1, Integer::sum);
        }

        // Détecter les événements manquants
        int index = 0;
        for (Map.Entry<String, Integer> entry : baselineCounts.entrySet()) {
            int baseCount = entry.getValue();
            int compCount = comparedCounts.getOrDefault(entry.getKey(), 0);

            if (compCount < baseCount) {
                for (int i = 0; i < baseCount - compCount; i++) {
                    TnrEvent fakeEvent = TnrEvent.builder()
                            .type("unknown")
                            .action(entry.getKey())
                            .build();
                    differences.add(TnrDifference.missing(index++, fakeEvent));
                }
            }
        }

        // Détecter les événements en plus
        for (Map.Entry<String, Integer> entry : comparedCounts.entrySet()) {
            int compCount = entry.getValue();
            int baseCount = baselineCounts.getOrDefault(entry.getKey(), 0);

            if (compCount > baseCount) {
                for (int i = 0; i < compCount - baseCount; i++) {
                    TnrEvent fakeEvent = TnrEvent.builder()
                            .type("unknown")
                            .action(entry.getKey())
                            .build();
                    differences.add(TnrDifference.extra(index++, fakeEvent));
                }
            }
        }
    }

    /**
     * Crée une clé unique pour un événement.
     */
    private String eventKey(TnrEvent event) {
        return (event.getType() != null ? event.getType() : "") + ":" +
               (event.getAction() != null ? event.getAction() : "");
    }

    /**
     * Vérifie si deux événements correspondent.
     */
    private boolean eventsMatch(TnrEvent base, TnrEvent comp, ComparisonOptions options) {
        if (!typeAndActionMatch(base, comp)) {
            return false;
        }

        // Si on ignore les payloads, c'est suffisant
        if (options.isIgnorePayloads()) {
            return true;
        }

        // Comparer les payloads
        return payloadsMatch(base.getPayload(), comp.getPayload());
    }

    /**
     * Vérifie si type et action correspondent.
     */
    private boolean typeAndActionMatch(TnrEvent base, TnrEvent comp) {
        boolean typeMatch = Objects.equals(base.getType(), comp.getType());
        boolean actionMatch = Objects.equals(base.getAction(), comp.getAction());
        return typeMatch && actionMatch;
    }

    /**
     * Compare deux payloads.
     */
    private boolean payloadsMatch(Object p1, Object p2) {
        if (p1 == null && p2 == null) return true;
        if (p1 == null || p2 == null) return false;

        // Comparaison simple par toString/hashCode
        return Objects.equals(p1.toString(), p2.toString());
    }

    /**
     * Détermine le verdict de la comparaison.
     */
    private Verdict determineVerdict(boolean signatureMatch, boolean criticalMatch,
                                       List<TnrDifference> differences, double similarity) {
        if (signatureMatch && differences.isEmpty()) {
            return Verdict.IDENTICAL;
        }

        if (!criticalMatch) {
            // Si les signatures critiques ne correspondent pas, c'est une régression
            return Verdict.REGRESSION;
        }

        // Vérifier s'il y a des différences critiques
        boolean hasCriticalDiffs = differences.stream().anyMatch(TnrDifference::isCritical);
        if (hasCriticalDiffs) {
            return Verdict.REGRESSION;
        }

        // Selon la similarité
        if (similarity >= 95) {
            return Verdict.COMPATIBLE;
        } else if (similarity >= 80) {
            return Verdict.DIFFERENT;
        } else {
            return Verdict.REGRESSION;
        }
    }

    /**
     * Compare avec une baseline sauvegardée par nom de scénario.
     */
    public TnrComparisonResult compareWithBaseline(String comparedId, String scenarioName) {
        // Chercher la dernière exécution "baseline" pour ce scénario
        Optional<TnrExecution> baseline = recordingService.getAllExecutions().stream()
                .filter(e -> scenarioName.equals(e.getScenarioName()))
                .filter(e -> !e.getId().equals(comparedId))
                .max(Comparator.comparing(TnrExecution::getStartedAt));

        if (baseline.isEmpty()) {
            throw new IllegalArgumentException("No baseline found for scenario: " + scenarioName);
        }

        return compare(baseline.get().getId(), comparedId, ComparisonOptions.defaults());
    }
}
