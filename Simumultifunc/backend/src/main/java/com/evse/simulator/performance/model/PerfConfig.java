package com.evse.simulator.performance.model;

import lombok.*;

/**
 * Configuration pour un test de performance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfConfig {
    private String csmsUrl;
    private String ocppUrl;
    private int targetConnections;
    private int rampUpSeconds;
    private int durationSeconds;
    private int durationMinutes;
    private int holdSeconds;
    private int messageIntervalMs;
    private int meterValueIntervalMs;
    private int meterValuesCount;
    private String chargerType;
    private String cpIdPrefix;
    private String idTagPrefix;
    private String idTag;
    private boolean sendMeterValues;
    private boolean sendHeartbeats;
    private int heartbeatIntervalSeconds;
    private int meterValuesIntervalSeconds;
    private String scenario;
    private ScenarioType scenarioType;
    private boolean autoReconnect;

    /**
     * Returns the scenario name or a default value.
     */
    public String getScenario() {
        return scenario != null ? scenario : "default";
    }

    /**
     * Gets OCPP URL, falling back to CSMS URL.
     */
    public String getOcppUrl() {
        return ocppUrl != null ? ocppUrl : csmsUrl;
    }

    /**
     * Gets idTag, falling back to idTagPrefix.
     */
    public String getIdTag() {
        return idTag != null ? idTag : (idTagPrefix != null ? idTagPrefix + "_TEST" : "TEST");
    }

    /**
     * Gets duration in minutes.
     */
    public int getDurationMinutes() {
        return durationMinutes > 0 ? durationMinutes : (durationSeconds / 60);
    }

    /**
     * Gets auto reconnect setting.
     */
    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    /**
     * Gets hold time in seconds.
     */
    public int getHoldSeconds() {
        return holdSeconds > 0 ? holdSeconds : 30;
    }

    /**
     * Gets meter value interval in milliseconds.
     */
    public int getMeterValueIntervalMs() {
        return meterValueIntervalMs > 0 ? meterValueIntervalMs : 1000;
    }

    /**
     * Gets number of meter values to send.
     */
    public int getMeterValuesCount() {
        return meterValuesCount > 0 ? meterValuesCount : 10;
    }

    /**
     * Enum for scenario types.
     */
    public enum ScenarioType {
        CONNECTION,
        CHARGING,
        STRESS,
        ENDURANCE
    }
}
