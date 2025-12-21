package com.evse.simulator.gpm.model.enums;

/**
 * Types de charge supportés par le simulateur GPM.
 */
public enum GPMChargeType {
    MONO("AC Monophasé", 1, 230, 32, 7360),
    TRI("AC Triphasé", 3, 400, 32, 22000),
    DC("DC Fast Charging", 0, 800, 500, 350000);

    private final String name;
    private final int phases;
    private final int voltage;
    private final int maxCurrentA;
    private final int maxPowerW;

    GPMChargeType(String name, int phases, int voltage, int maxCurrentA, int maxPowerW) {
        this.name = name;
        this.phases = phases;
        this.voltage = voltage;
        this.maxCurrentA = maxCurrentA;
        this.maxPowerW = maxPowerW;
    }

    public String getDisplayName() { return name; }
    public String getLabel() { return name; }
    public int getPhases() { return phases; }
    public int getVoltage() { return voltage; }
    public double getVoltageV() { return voltage; }
    public int getMaxCurrentA() { return maxCurrentA; }
    public int getMaxPowerW() { return maxPowerW; }

    public boolean isAC() { return this != DC; }
    public boolean isDC() { return this == DC; }
}
