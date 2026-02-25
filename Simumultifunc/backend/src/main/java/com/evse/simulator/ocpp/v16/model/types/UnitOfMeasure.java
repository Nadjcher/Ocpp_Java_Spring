package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Unit of measure for sampled values.
 */
public enum UnitOfMeasure {
    WH("Wh"),
    KWH("kWh"),
    VARH("varh"),
    KVARH("kvarh"),
    W("W"),
    KW("kW"),
    VA("VA"),
    KVA("kVA"),
    VAR("var"),
    KVAR("kvar"),
    A("A"),
    V("V"),
    K("K"),
    CELSIUS("Celsius"),
    FAHRENHEIT("Fahrenheit"),
    PERCENT("Percent");

    private final String value;

    UnitOfMeasure(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static UnitOfMeasure fromValue(String value) {
        for (UnitOfMeasure unit : values()) {
            if (unit.value.equalsIgnoreCase(value)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Unknown UnitOfMeasure: " + value);
    }
}
