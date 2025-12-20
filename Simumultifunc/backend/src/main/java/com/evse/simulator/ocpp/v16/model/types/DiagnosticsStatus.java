package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status for DiagnosticsStatusNotification.
 */
public enum DiagnosticsStatus {
    IDLE("Idle"),
    UPLOADED("Uploaded"),
    UPLOAD_FAILED("UploadFailed"),
    UPLOADING("Uploading");

    private final String value;

    DiagnosticsStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DiagnosticsStatus fromValue(String value) {
        for (DiagnosticsStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown DiagnosticsStatus: " + value);
    }
}
