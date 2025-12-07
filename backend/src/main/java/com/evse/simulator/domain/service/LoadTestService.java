package com.evse.simulator.domain.service;

import com.evse.simulator.model.Session;

import java.util.List;

/**
 * Service de gestion des tests de charge.
 */
public interface LoadTestService {

    /**
     * Démarre un test de charge.
     *
     * @param targetSessions nombre de sessions cibles
     * @param rampUpSeconds temps de montée en charge en secondes
     * @param holdSeconds temps de maintien des connexions en secondes
     * @param sessionTemplate modèle de session à utiliser
     * @return ID unique du test
     */
    String startLoadTest(int targetSessions, int rampUpSeconds, int holdSeconds, Session sessionTemplate);

    /**
     * Démarre un test de charge avec une liste de sessions pré-configurées.
     * Chaque session a son propre cpId et idTag.
     *
     * @param sessionConfigs liste des configurations de sessions (cpId, idTag, url, etc.)
     * @param rampUpSeconds temps de montée en charge en secondes
     * @param holdSeconds temps de maintien des connexions en secondes
     * @return ID unique du test
     */
    String startLoadTestWithList(List<java.util.Map<String, Object>> sessionConfigs, int rampUpSeconds, int holdSeconds);

    /**
     * Démarre un test de charge avec configuration map (compatibilité frontend).
     *
     * @param config configuration du test
     * @return ID unique du test
     */
    default String startLoadTest(java.util.Map<String, Object> config) {
        // Log la config reçue
        System.out.println("[LoadTestService] Received config keys: " + config.keySet());
        System.out.println("[LoadTestService] url=" + config.get("url"));
        System.out.println("[LoadTestService] csvText present=" + config.containsKey("csvText") +
                           ", value length=" + (config.get("csvText") != null ? String.valueOf(config.get("csvText")).length() : 0));

        // Récupérer l'URL de la config (toujours présente dans le frontend)
        String url = config.containsKey("url") ? (String) config.get("url") : null;
        int rampUp = config.containsKey("rampUp")
            ? ((Number) config.get("rampUp")).intValue() : 10;
        int holdSec = config.containsKey("holdSec")
            ? ((Number) config.get("holdSec")).intValue() : 60;

        // Cas 1: CSV avec liste de sessions pré-parsée (depuis /api/perf/import)
        if (config.containsKey("sessions") && config.get("sessions") instanceof List) {
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> sessionConfigs =
                (List<java.util.Map<String, Object>>) config.get("sessions");

            System.out.println("[LoadTestService] Cas 1: sessions list with " + sessionConfigs.size() + " items");

            // Ajouter l'URL à chaque session si non présente
            if (url != null) {
                for (java.util.Map<String, Object> sc : sessionConfigs) {
                    if (!sc.containsKey("url") || sc.get("url") == null) {
                        sc.put("url", url);
                    }
                }
            }
            return startLoadTestWithList(sessionConfigs, rampUp, holdSec);
        }

        // Cas 2: csvText brut du frontend (format "cpId,idTag\nawsevse0500,TAG-0500")
        if (config.containsKey("csvText") && config.get("csvText") != null) {
            String csvText = (String) config.get("csvText");
            System.out.println("[LoadTestService] Cas 2: csvText with " + csvText.length() + " chars");
            System.out.println("[LoadTestService] csvText preview: " + csvText.substring(0, Math.min(200, csvText.length())));

            List<java.util.Map<String, Object>> sessionConfigs = parseCsvText(csvText, url);
            System.out.println("[LoadTestService] Parsed " + sessionConfigs.size() + " sessions from CSV");

            if (!sessionConfigs.isEmpty()) {
                if (sessionConfigs.size() <= 3) {
                    System.out.println("[LoadTestService] First sessions: " + sessionConfigs);
                } else {
                    System.out.println("[LoadTestService] First 3 sessions: " + sessionConfigs.subList(0, 3));
                }
                return startLoadTestWithList(sessionConfigs, rampUp, holdSec);
            } else {
                System.out.println("[LoadTestService] WARNING: CSV parsing returned empty list!");
            }
        }

        // Cas 3: Mode standard avec génération automatique
        int sessions = config.containsKey("sessions")
            ? ((Number) config.get("sessions")).intValue() : 10;

        System.out.println("[LoadTestService] Cas 3: Standard mode with " + sessions + " sessions");

        Session template = null;
        if (url != null) {
            template = Session.builder()
                .url(url)
                .cpId(config.containsKey("cpId") ? (String) config.get("cpId") : null)
                .idTag(config.containsKey("idTag") ? (String) config.get("idTag") : "PERF-TAG")
                .build();
        }

        return startLoadTest(sessions, rampUp, holdSec, template);
    }

    /**
     * Parse le texte CSV en liste de configurations de sessions.
     * Format attendu: cpId,idTag (avec ou sans header)
     */
    default List<java.util.Map<String, Object>> parseCsvText(String csvText, String defaultUrl) {
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        if (csvText == null || csvText.isBlank()) return result;

        String[] lines = csvText.split("\n");
        boolean hasHeader = false;
        int cpIdIdx = 0, idTagIdx = 1, urlIdx = -1;

        // Détecter le header
        if (lines.length > 0) {
            String firstLine = lines[0].trim().toLowerCase();
            if (firstLine.contains("cpid") || firstLine.contains("idtag")) {
                hasHeader = true;
                String[] headers = lines[0].split("[,;|\\s]+");
                for (int h = 0; h < headers.length; h++) {
                    String header = headers[h].trim().toLowerCase();
                    if (header.equals("cpid")) cpIdIdx = h;
                    else if (header.equals("idtag")) idTagIdx = h;
                    else if (header.equals("url")) urlIdx = h;
                }
            }
        }

        int startIdx = hasHeader ? 1 : 0;
        for (int i = startIdx; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] values = line.split("[,;|\\s]+");
            if (values.length == 0 || values[0].isBlank()) continue;

            String cpId = values.length > cpIdIdx ? values[cpIdIdx].trim() : null;
            String idTag = values.length > idTagIdx ? values[idTagIdx].trim() : "TAG";
            String url = (urlIdx >= 0 && values.length > urlIdx) ? values[urlIdx].trim() : defaultUrl;

            if (cpId != null && !cpId.isEmpty()) {
                java.util.Map<String, Object> sessionConfig = new java.util.HashMap<>();
                sessionConfig.put("cpId", cpId);
                sessionConfig.put("idTag", idTag);
                sessionConfig.put("url", url);
                result.add(sessionConfig);
            }
        }

        return result;
    }

    /**
     * Arrête le test de charge en cours.
     */
    void stopLoadTest();

    /**
     * Vérifie si un test de charge est en cours.
     *
     * @return true si un test est en cours
     */
    boolean isLoadTestRunning();

    /**
     * Alias pour isLoadTestRunning() (compatibilité frontend).
     */
    default boolean isRunning() {
        return isLoadTestRunning();
    }

    /**
     * Retourne l'ID du test en cours.
     *
     * @return ID du test ou null
     */
    default String getCurrentRunId() {
        LoadTestStatus status = getLoadTestStatus();
        return status != null && status.isRunning() ? "run-" + status.getStartTime() : null;
    }

    /**
     * Récupère l'état du test de charge.
     *
     * @return état du test ou null si pas de test
     */
    LoadTestStatus getLoadTestStatus();

    /**
     * Récupère les statistiques par session.
     *
     * @return liste des statistiques
     */
    List<SessionStats> getSessionStats();

    /**
     * État d'un test de charge.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class LoadTestStatus {
        private boolean running;
        private int targetSessions;
        private int currentSessions;
        private int connectedSessions;
        private long startTime;
        private long durationMs;
        private long messagesSent;
        private long messagesReceived;
        private long errors;
        private double progress;
    }

    /**
     * Statistiques d'une session.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class SessionStats {
        private String sessionId;
        private String cpId;
        private String state;
        private boolean connected;
        private boolean charging;
        private double soc;
        private double powerKw;
        private double energyKwh;
        private int messageCount;
    }
}
