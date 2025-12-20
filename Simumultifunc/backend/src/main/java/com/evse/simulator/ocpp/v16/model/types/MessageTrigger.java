package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Message types that can be triggered via TriggerMessage.
 */
public enum MessageTrigger {
    BOOT_NOTIFICATION("BootNotification"),
    DIAGNOSTICS_STATUS_NOTIFICATION("DiagnosticsStatusNotification"),
    FIRMWARE_STATUS_NOTIFICATION("FirmwareStatusNotification"),
    HEARTBEAT("Heartbeat"),
    METER_VALUES("MeterValues"),
    STATUS_NOTIFICATION("StatusNotification");

    private final String value;

    MessageTrigger(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MessageTrigger fromValue(String value) {
        for (MessageTrigger trigger : values()) {
            if (trigger.value.equalsIgnoreCase(value)) {
                return trigger;
            }
        }
        throw new IllegalArgumentException("Unknown MessageTrigger: " + value);
    }
}
