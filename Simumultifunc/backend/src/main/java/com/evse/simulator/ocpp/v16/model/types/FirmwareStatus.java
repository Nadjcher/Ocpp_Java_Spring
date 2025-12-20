package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status for FirmwareStatusNotification.
 */
public enum FirmwareStatus {
    DOWNLOADED("Downloaded"),
    DOWNLOAD_FAILED("DownloadFailed"),
    DOWNLOADING("Downloading"),
    IDLE("Idle"),
    INSTALLATION_FAILED("InstallationFailed"),
    INSTALLING("Installing"),
    INSTALLED("Installed");

    private final String value;

    FirmwareStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static FirmwareStatus fromValue(String value) {
        for (FirmwareStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown FirmwareStatus: " + value);
    }
}
