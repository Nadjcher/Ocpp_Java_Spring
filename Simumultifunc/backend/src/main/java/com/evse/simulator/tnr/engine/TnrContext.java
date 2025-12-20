package com.evse.simulator.tnr.engine;

import com.evse.simulator.tnr.model.OcppMessageCapture;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contexte partagé pour l'exécution d'un scénario TNR.
 * Stocke les variables, captures OCPP, et données partagées entre steps.
 */
@Slf4j
public class TnrContext {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /** Variables stockées */
    private final Map<String, Object> variables = new ConcurrentHashMap<>();

    /** Listes (pour addToList) */
    private final Map<String, List<Object>> lists = new ConcurrentHashMap<>();

    /** Messages OCPP capturés */
    @Getter
    private final List<OcppMessageCapture> messageHistory = Collections.synchronizedList(new ArrayList<>());

    /** ID de session courant */
    @Getter
    private String currentSessionId;

    /** IDs de toutes les sessions actives */
    @Getter
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    /** Compteurs auto-incrémentés */
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /** Timestamp de début */
    @Getter
    private final Instant startTime = Instant.now();

    /** Métriques collectées */
    private final Map<String, Object> metrics = new ConcurrentHashMap<>();

    // =========================================================================
    // Gestion des variables
    // =========================================================================

    /**
     * Stocke une variable.
     */
    public void set(String key, Object value) {
        variables.put(key, value);
        log.debug("TNR Context: set {}={}", key, value);
    }

    /**
     * Récupère une variable.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) variables.get(key);
    }

    /**
     * Récupère une variable avec valeur par défaut.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) variables.getOrDefault(key, defaultValue);
    }

    /**
     * Vérifie si une variable existe.
     */
    public boolean has(String key) {
        return variables.containsKey(key);
    }

    /**
     * Supprime une variable.
     */
    public void remove(String key) {
        variables.remove(key);
    }

    /**
     * Retourne toutes les variables.
     */
    public Map<String, Object> getAllVariables() {
        return new HashMap<>(variables);
    }

    // =========================================================================
    // Gestion des listes
    // =========================================================================

    /**
     * Ajoute un élément à une liste.
     */
    public void addToList(String listName, Object value) {
        lists.computeIfAbsent(listName, k -> Collections.synchronizedList(new ArrayList<>())).add(value);
    }

    /**
     * Récupère une liste.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String listName) {
        return (List<T>) lists.getOrDefault(listName, Collections.emptyList());
    }

    // =========================================================================
    // Session management
    // =========================================================================

    /**
     * Définit la session courante.
     */
    public void setCurrentSessionId(String sessionId) {
        this.currentSessionId = sessionId;
        if (sessionId != null) {
            activeSessions.add(sessionId);
        }
    }

    /**
     * Ajoute une session active.
     */
    public void addActiveSession(String sessionId) {
        activeSessions.add(sessionId);
    }

    /**
     * Supprime une session.
     */
    public void removeSession(String sessionId) {
        activeSessions.remove(sessionId);
        if (sessionId.equals(currentSessionId)) {
            currentSessionId = activeSessions.stream().findFirst().orElse(null);
        }
    }

    // =========================================================================
    // Message history
    // =========================================================================

    /**
     * Ajoute un message OCPP au historique.
     */
    public void addOcppMessage(OcppMessageCapture message) {
        messageHistory.add(message);
    }

    /**
     * Récupère les messages par action.
     */
    public List<OcppMessageCapture> getMessagesByAction(String action) {
        return messageHistory.stream()
            .filter(m -> action.equals(m.getAction()))
            .toList();
    }

    /**
     * Récupère le dernier message d'une action.
     */
    public Optional<OcppMessageCapture> getLastMessageByAction(String action) {
        for (int i = messageHistory.size() - 1; i >= 0; i--) {
            OcppMessageCapture m = messageHistory.get(i);
            if (action.equals(m.getAction())) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    // =========================================================================
    // Counters
    // =========================================================================

    /**
     * Incrémente un compteur et retourne la nouvelle valeur.
     */
    public int incrementCounter(String name) {
        return counters.computeIfAbsent(name, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Récupère un compteur.
     */
    public int getCounter(String name) {
        AtomicInteger counter = counters.get(name);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Génère un prochain ID de profil.
     */
    public int nextProfileId() {
        return incrementCounter("profileId");
    }

    // =========================================================================
    // Variable resolution
    // =========================================================================

    /**
     * Résout les variables ${...} dans une chaîne.
     */
    public String resolveVariables(String text) {
        if (text == null || !text.contains("${")) {
            return text;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String expression = matcher.group(1);
            Object value = evaluateExpression(expression);
            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Évalue une expression variable.
     */
    private Object evaluateExpression(String expression) {
        // Fonctions spéciales
        if (expression.equals("uuid()")) {
            return UUID.randomUUID().toString();
        }
        if (expression.equals("now()")) {
            return Instant.now().toString();
        }
        if (expression.equals("timestamp()")) {
            return System.currentTimeMillis();
        }
        if (expression.startsWith("random(")) {
            int length = Integer.parseInt(expression.substring(7, expression.length() - 1));
            return generateRandomString(length);
        }
        if (expression.equals("autoIncrement()")) {
            return incrementCounter("autoIncrement");
        }
        if (expression.startsWith("env.")) {
            String envVar = expression.substring(4);
            return System.getenv().getOrDefault(envVar, "");
        }

        // Accès à une propriété (ex: response.transactionId)
        if (expression.contains(".")) {
            String[] parts = expression.split("\\.", 2);
            Object obj = variables.get(parts[0]);
            if (obj instanceof JsonNode) {
                return ((JsonNode) obj).path(parts[1]).asText();
            }
            if (obj instanceof Map) {
                return ((Map<?, ?>) obj).get(parts[1]);
            }
        }

        // Variable simple
        return variables.getOrDefault(expression, "${" + expression + "}");
    }

    /**
     * Génère une chaîne aléatoire.
     */
    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    /**
     * Ajoute une métrique.
     */
    public void addMetric(String key, Object value) {
        metrics.put(key, value);
    }

    /**
     * Récupère les métriques.
     */
    public Map<String, Object> getMetrics() {
        return new HashMap<>(metrics);
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    /**
     * Nettoie le contexte.
     */
    public void clear() {
        variables.clear();
        lists.clear();
        messageHistory.clear();
        activeSessions.clear();
        counters.clear();
        metrics.clear();
        currentSessionId = null;
    }

    /**
     * Crée un snapshot du contexte.
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("variables", new HashMap<>(variables));
        snapshot.put("lists", new HashMap<>(lists));
        snapshot.put("messageCount", messageHistory.size());
        snapshot.put("activeSessions", new HashSet<>(activeSessions));
        snapshot.put("currentSessionId", currentSessionId);
        snapshot.put("metrics", new HashMap<>(metrics));
        return snapshot;
    }
}
