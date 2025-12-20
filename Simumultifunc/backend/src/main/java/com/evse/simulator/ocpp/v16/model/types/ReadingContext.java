package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ReadingContext {
    INTERRUPTION_BEGIN("Interruption.Begin"),
    INTERRUPTION_END("Interruption.End"),
    OTHER("Other"),
    SAMPLE_CLOCK("Sample.Clock"),
    SAMPLE_PERIODIC("Sample.Periodic"),
    TRANSACTION_BEGIN("Transaction.Begin"),
    TRANSACTION_END("Transaction.End"),
    TRIGGER("Trigger");

    private final String value;

    ReadingContext(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
