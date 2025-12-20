package com.evse.simulator.tnr.service;

import com.evse.simulator.tnr.model.TnrEvent;
import com.evse.simulator.tnr.model.enums.TnrEventCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de calcul de signatures pour les exécutions TNR.
 * <p>
 * Utilise SHA-256 pour des signatures robustes sans collisions.
 * Supporte les signatures partielles pour comparaisons flexibles.
 * </p>
 */
@Service
@Slf4j
public class TnrSignatureService {

    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * Calcule la signature complète d'une liste d'événements.
     *
     * @param events liste des événements
     * @return signature SHA-256 en hexadécimal
     */
    public String computeSignature(List<TnrEvent> events) {
        if (events == null || events.isEmpty()) {
            return "empty";
        }

        StringBuilder sb = new StringBuilder();
        for (TnrEvent ev : events) {
            sb.append(ev.getType() != null ? ev.getType() : "null")
              .append(":")
              .append(ev.getAction() != null ? ev.getAction() : "null")
              .append(":")
              .append(ev.getDirection() != null ? ev.getDirection() : "")
              .append(":")
              .append(computePayloadHash(ev.getPayload()))
              .append(";");
        }

        return sha256Hex(sb.toString());
    }

    /**
     * Calcule une signature partielle basée sur certaines catégories.
     *
     * @param events liste des événements
     * @param categories catégories à inclure
     * @return signature partielle
     */
    public String computePartialSignature(List<TnrEvent> events, TnrEventCategory... categories) {
        if (events == null || events.isEmpty()) {
            return "empty";
        }

        Set<TnrEventCategory> categorySet = new HashSet<>(Arrays.asList(categories));
        List<TnrEvent> filtered = events.stream()
                .filter(e -> categorySet.contains(e.getCategory()))
                .collect(Collectors.toList());

        return computeSignature(filtered);
    }

    /**
     * Calcule une signature pour les événements critiques seulement.
     * (TRANSACTION, AUTHENTICATION, ERROR)
     *
     * @param events liste des événements
     * @return signature des événements critiques
     */
    public String computeCriticalSignature(List<TnrEvent> events) {
        return computePartialSignature(events,
                TnrEventCategory.TRANSACTION,
                TnrEventCategory.AUTHENTICATION,
                TnrEventCategory.ERROR);
    }

    /**
     * Calcule une signature pour la séquence OCPP seulement.
     * Ignore les heartbeats et événements système.
     *
     * @param events liste des événements
     * @return signature OCPP
     */
    public String computeOcppSequenceSignature(List<TnrEvent> events) {
        if (events == null || events.isEmpty()) {
            return "empty";
        }

        // Filtrer les Heartbeats et événements internes
        List<TnrEvent> ocppEvents = events.stream()
                .filter(e -> "ocpp".equalsIgnoreCase(e.getType()))
                .filter(e -> !"Heartbeat".equalsIgnoreCase(e.getAction()))
                .collect(Collectors.toList());

        return computeSignature(ocppEvents);
    }

    /**
     * Calcule une signature légère (type:action seulement, sans payload).
     *
     * @param events liste des événements
     * @return signature légère
     */
    public String computeLightSignature(List<TnrEvent> events) {
        if (events == null || events.isEmpty()) {
            return "empty";
        }

        StringBuilder sb = new StringBuilder();
        for (TnrEvent ev : events) {
            sb.append(ev.getType() != null ? ev.getType() : "null")
              .append(":")
              .append(ev.getAction() != null ? ev.getAction() : "null")
              .append(";");
        }

        return sha256Hex(sb.toString());
    }

    /**
     * Calcule le hash d'un payload.
     */
    private String computePayloadHash(Object payload) {
        if (payload == null) {
            return "null";
        }

        try {
            String payloadStr;
            if (payload instanceof Map || payload instanceof Collection) {
                // Pour les structures complexes, utiliser le hashCode
                payloadStr = String.valueOf(Objects.hashCode(payload));
            } else {
                payloadStr = payload.toString();
            }
            // Tronquer pour éviter les signatures trop longues
            if (payloadStr.length() > 1000) {
                payloadStr = payloadStr.substring(0, 1000);
            }
            return String.valueOf(payloadStr.hashCode());
        } catch (Exception e) {
            return "error";
        }
    }

    /**
     * Calcule le SHA-256 d'une chaîne.
     */
    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            // Fallback to simple hash
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * Convertit des bytes en hexadécimal.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Compare deux signatures.
     *
     * @param sig1 première signature
     * @param sig2 deuxième signature
     * @return true si identiques
     */
    public boolean signaturesMatch(String sig1, String sig2) {
        if (sig1 == null || sig2 == null) return false;
        return sig1.equals(sig2);
    }

    /**
     * Calcule le pourcentage de similarité entre deux séquences d'événements.
     * Utilise l'algorithme de Levenshtein sur les actions.
     *
     * @param events1 première séquence
     * @param events2 deuxième séquence
     * @return pourcentage de similarité (0-100)
     */
    public double computeSimilarity(List<TnrEvent> events1, List<TnrEvent> events2) {
        if (events1 == null || events2 == null) return 0;
        if (events1.isEmpty() && events2.isEmpty()) return 100;
        if (events1.isEmpty() || events2.isEmpty()) return 0;

        List<String> seq1 = events1.stream()
                .map(e -> e.getType() + ":" + e.getAction())
                .collect(Collectors.toList());
        List<String> seq2 = events2.stream()
                .map(e -> e.getType() + ":" + e.getAction())
                .collect(Collectors.toList());

        int distance = levenshteinDistance(seq1, seq2);
        int maxLen = Math.max(seq1.size(), seq2.size());

        return (1.0 - (double) distance / maxLen) * 100;
    }

    /**
     * Calcule la distance de Levenshtein entre deux listes.
     */
    private int levenshteinDistance(List<String> s1, List<String> s2) {
        int m = s1.size();
        int n = s2.size();

        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = s1.get(i - 1).equals(s2.get(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[m][n];
    }
}
