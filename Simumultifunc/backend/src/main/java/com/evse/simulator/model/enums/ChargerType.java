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
            // AC: P = V × I × factor
            // Triphasé avec tension phase-neutre (230V): factor = phases
            // Triphasé avec tension ligne-ligne (400V): factor = √3
            // Monophasé: factor = 1
            double factor;
            if (phases == 1) {
                factor = 1.0;
            } else if (voltage < 300) {
                factor = phases;  // 2 ou 3 selon le nombre de phases
            } else {
                factor = Math.sqrt(3);
            }
            return (voltage * currentA * factor) / 1000.0;
        }
    }

    /**
     * Convertit une chaîne en ChargerType.
     * Supporte les formats: "ac-mono", "ac_mono", "AC_MONO", etc.
     */
    public static ChargerType fromValue(String value) {
        if (value == null) {
            return AC_TRI;
        }

        // Normaliser: remplacer les tirets par des underscores et mettre en majuscules
        String normalized = value.toUpperCase().replace("-", "_");

        for (ChargerType type : values()) {
            if (type.value.equalsIgnoreCase(normalized) ||
                type.name().equalsIgnoreCase(normalized) ||
                type.value.equalsIgnoreCase(value) ||
                type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }

        // Fallback pour compatibilité avec formats courts
        if (normalized.contains("DC")) {
            if (normalized.contains("150")) return DC_150;
            if (normalized.contains("350")) return DC_350;
            if (normalized.contains("50")) return DC_50;
            return DC;
        }
        if (normalized.contains("MONO") || normalized.equals("AC_1") || normalized.equals("AC1")) {
            return AC_MONO;
        }
        if (normalized.contains("BI") || normalized.equals("AC_2") || normalized.equals("AC2")) {
            return AC_BI;
        }
        if (normalized.contains("TRI") || normalized.contains("43")) {
            return normalized.contains("43") ? AC_TRI_43 : AC_TRI;
        }

        // Fallback par défaut
        return AC_TRI;
    }

    @Override
    public String toString() {
        return value;
    }
}