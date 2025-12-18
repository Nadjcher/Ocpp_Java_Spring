package com.evse.simulator.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Types de chargeurs EVSE supportés.
 * <p>
 * Définit les caractéristiques électriques du point de charge.
 * </p>
 */
public enum ChargerType {

    /**
     * Chargeur AC monophasé (230V, max 7.4 kW).
     */
    AC_MONO("AC_MONO", 1, 230, 32, 7.4),

    /**
     * Chargeur AC biphasé (400V entre phases, max 14.5 kW).
     */
    AC_BI("AC_BI", 2, 400, 32, 14.5),

    /**
     * Chargeur AC triphasé (400V, max 22 kW standard, 43 kW max).
     */
    AC_TRI("AC_TRI", 3, 400, 32, 22.0),

    /**
     * Chargeur AC triphasé haute puissance (400V, max 43 kW).
     */
    AC_TRI_43("AC_TRI_43", 3, 400, 63, 43.0),

    /**
     * Chargeur DC standard (max 50 kW).
     */
    DC_50("DC_50", 0, 500, 125, 50.0),

    /**
     * Chargeur DC rapide (max 150 kW).
     */
    DC_150("DC_150", 0, 500, 350, 150.0),

    /**
     * Chargeur DC ultra-rapide (max 350 kW).
     */
    DC_350("DC_350", 0, 920, 500, 350.0),

    /**
     * Alias pour DC standard.
     */
    DC("DC", 0, 500, 125, 50.0);

    private final String value;
    private final int phases;
    private final double voltage;
    private final double maxCurrentA;
    private final double maxPowerKw;

    ChargerType(String value, int phases, double voltage, double maxCurrentA, double maxPowerKw) {
        this.value = value;
        this.phases = phases;
        this.voltage = voltage;
        this.maxCurrentA = maxCurrentA;
        this.maxPowerKw = maxPowerKw;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Nombre de phases (0 pour DC).
     */
    public int getPhases() {
        return phases;
    }

    /**
     * Tension nominale en Volts.
     */
    public double getVoltage() {
        return voltage;
    }

    /**
     * Courant maximum en Ampères.
     */
    public double getMaxCurrentA() {
        return maxCurrentA;
    }

    /**
     * Puissance maximum en kW.
     */
    public double getMaxPowerKw() {
        return maxPowerKw;
    }

    /**
     * Vérifie si c'est un chargeur DC.
     */
    public boolean isDC() {
        return phases == 0;
    }

    /**
     * Vérifie si c'est un chargeur AC.
     */
    public boolean isAC() {
        return phases > 0;
    }

    /**
     * Calcule la puissance pour un courant donné.
     *
     * @param currentA courant en Ampères
     * @return puissance en kW
     */
    public double calculatePower(double currentA) {
        if (isDC()) {
            return (voltage * currentA) / 1000.0;
        } else {
            // AC: P = U * I * sqrt(3) * cos(phi) pour triphasé
            // Simplifié avec cos(phi) = 1
            double factor = phases == 1 ? 1.0 : (phases == 2 ? 2.0 : Math.sqrt(3));
            return (voltage * currentA * factor) / 1000.0;
        }
    }

    /**
     * Convertit une chaîne en ChargerType.
     */
    public static ChargerType fromValue(String value) {
        if (value == null) {
            return AC_TRI;
        }
        for (ChargerType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        // Fallback pour compatibilité
        if (value.toUpperCase().contains("DC")) {
            return DC;
        }
        return AC_TRI;
    }

    @Override
    public String toString() {
        return value;
    }
}