package com.evse.simulator.tnr.model;

import lombok.*;

/**
 * Différence trouvée lors d'une comparaison TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrDifference {
    private String path;
    private Object expected;
    private Object actual;
    private String type;
    private String message;
    private int index;
    private TnrEvent baselineEvent;
    private TnrEvent comparedEvent;
    private boolean critical;

    /**
     * Creates a difference for an extra event.
     */
    public static TnrDifference extra(int index, TnrEvent event) {
        return TnrDifference.builder()
                .index(index)
                .type("EXTRA")
                .comparedEvent(event)
                .message("Extra event at index " + index + ": " + event.getAction())
                .critical(event.isCritical())
                .build();
    }

    /**
     * Creates a difference for a missing event.
     */
    public static TnrDifference missing(int index, TnrEvent event) {
        return TnrDifference.builder()
                .index(index)
                .type("MISSING")
                .baselineEvent(event)
                .message("Missing event at index " + index + ": " + event.getAction())
                .critical(event.isCritical())
                .build();
    }

    /**
     * Creates a difference for type change.
     */
    public static TnrDifference typeChanged(int index, TnrEvent baseline, TnrEvent compared) {
        return TnrDifference.builder()
                .index(index)
                .type("TYPE_CHANGED")
                .baselineEvent(baseline)
                .comparedEvent(compared)
                .expected(baseline.getType())
                .actual(compared.getType())
                .message("Type changed at index " + index + ": " + baseline.getType() + " -> " + compared.getType())
                .critical(baseline.isCritical() || compared.isCritical())
                .build();
    }

    /**
     * Creates a difference for modified content.
     */
    public static TnrDifference modified(int index, TnrEvent baseline, TnrEvent compared, String details) {
        return TnrDifference.builder()
                .index(index)
                .type("MODIFIED")
                .baselineEvent(baseline)
                .comparedEvent(compared)
                .message("Modified event at index " + index + ": " + details)
                .critical(baseline.isCritical() || compared.isCritical())
                .build();
    }

    /**
     * Checks if this difference is critical.
     */
    public boolean isCritical() {
        if (critical) return true;
        if (baselineEvent != null && baselineEvent.isCritical()) return true;
        if (comparedEvent != null && comparedEvent.isCritical()) return true;
        return false;
    }
}
