package com.evse.simulator.ocpi;

public enum OCPIModule {
    CREDENTIALS("credentials"),
    LOCATIONS("locations"),
    SESSIONS("sessions"),
    CDRS("cdrs"),
    TARIFFS("tariffs"),
    TOKENS("tokens"),
    COMMANDS("commands"),
    CHARGING_PROFILES("chargingprofiles"),
    HUB_CLIENT_INFO("hubclientinfo");

    private final String value;

    OCPIModule(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OCPIModule fromValue(String value) {
        for (OCPIModule module : values()) {
            if (module.value.equalsIgnoreCase(value)) {
                return module;
            }
        }
        throw new IllegalArgumentException("Unknown module: " + value);
    }
}
