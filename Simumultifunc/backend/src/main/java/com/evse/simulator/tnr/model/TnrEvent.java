package com.evse.simulator.tnr.model;

import com.evse.simulator.tnr.model.enums.TnrEventCategory;
import lombok.*;

import java.time.Instant;
import java.util.Map;

/**
 * Événement enregistré pendant un test TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrEvent {
    private String id;
    private String type;
    private TnrEventCategory category;
    private String action;
    private String direction;
    private Instant timestamp;
    private Instant occurredAt;
    private long relativeTimeMs;
    private int sequenceIndex;
    private Map<String, Object> payload;
    private String rawMessage;
    private TnrAttachment attachment;
    private boolean critical;

    /**
     * Gets the event type.
     */
    public String getType() {
        if (type != null) return type;
        if (category != null) return category.name();
        return action;
    }

    /**
     * Gets occurred at time, falling back to timestamp.
     */
    public Instant getOccurredAt() {
        return occurredAt != null ? occurredAt : timestamp;
    }

    /**
     * Checks if this event is critical.
     */
    public boolean isCritical() {
        if (critical) return true;
        // Transaction events are always critical
        if (action != null) {
            String lowerAction = action.toLowerCase();
            return lowerAction.contains("transaction") ||
                   lowerAction.contains("authorize") ||
                   lowerAction.contains("boot");
        }
        return false;
    }

    /**
     * Returns a short string representation.
     */
    public String toShortString() {
        StringBuilder sb = new StringBuilder();
        if (direction != null) {
            sb.append(direction).append(" ");
        }
        if (action != null) {
            sb.append(action);
        } else if (category != null) {
            sb.append(category.name());
        }
        return sb.toString();
    }

    /**
     * Creates an OCPP receive event.
     */
    public static TnrEvent ocppRecv(String action, String messageId, Object payload, String rawMessage) {
        return TnrEvent.builder()
                .action(action)
                .category(TnrEventCategory.OCPP_RESPONSE)
                .direction("RECV")
                .payload(payload instanceof Map ? (Map<String, Object>) payload : null)
                .rawMessage(rawMessage)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates an OCPP send event.
     */
    public static TnrEvent ocppSend(String action, String messageId, Object payload) {
        return TnrEvent.builder()
                .action(action)
                .category(TnrEventCategory.OCPP_REQUEST)
                .direction("SEND")
                .payload(payload instanceof Map ? (Map<String, Object>) payload : null)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a connection event.
     */
    public static TnrEvent connection(String cpId, boolean connected) {
        return TnrEvent.builder()
                .action(connected ? "CONNECT" : "DISCONNECT")
                .category(TnrEventCategory.CONNECTION)
                .direction("EVENT")
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a TnrEvent from a legacy TNREvent.
     */
    @SuppressWarnings("unchecked")
    public static TnrEvent fromLegacy(com.evse.simulator.model.TNREvent legacy) {
        if (legacy == null) return null;

        // Convert Long timestamp to Instant
        Instant ts = legacy.getTimestamp() != null
                ? Instant.ofEpochMilli(legacy.getTimestamp())
                : Instant.now();

        return TnrEvent.builder()
                .action(legacy.getAction())
                .type(legacy.getType())
                .timestamp(ts)
                .payload(legacy.getPayload() instanceof Map ? (Map<String, Object>) legacy.getPayload() : null)
                .build();
    }
}
