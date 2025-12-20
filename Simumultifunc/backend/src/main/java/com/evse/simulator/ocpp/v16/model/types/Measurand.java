package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Measurand {
    ENERGY_ACTIVE_EXPORT_REGISTER("Energy.Active.Export.Register"),
    ENERGY_ACTIVE_IMPORT_REGISTER("Energy.Active.Import.Register"),
    ENERGY_REACTIVE_EXPORT_REGISTER("Energy.Reactive.Export.Register"),
    ENERGY_REACTIVE_IMPORT_REGISTER("Energy.Reactive.Import.Register"),
    ENERGY_ACTIVE_EXPORT_INTERVAL("Energy.Active.Export.Interval"),
    ENERGY_ACTIVE_IMPORT_INTERVAL("Energy.Active.Import.Interval"),
    POWER_ACTIVE_EXPORT("Power.Active.Export"),
    POWER_ACTIVE_IMPORT("Power.Active.Import"),
    POWER_OFFERED("Power.Offered"),
    CURRENT_IMPORT("Current.Import"),
    CURRENT_EXPORT("Current.Export"),
    CURRENT_OFFERED("Current.Offered"),
    VOLTAGE("Voltage"),
    FREQUENCY("Frequency"),
    TEMPERATURE("Temperature"),
    SOC("SoC"),
    RPM("RPM");

    private final String value;

    Measurand(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
