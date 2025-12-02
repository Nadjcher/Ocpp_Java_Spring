package com.evse.simulator.exception;

/**
 * Exception levée quand un véhicule n'est pas trouvé.
 */
public class VehicleNotFoundException extends RuntimeException {

    public VehicleNotFoundException(String vehicleId) {
        super("Vehicle profile not found: " + vehicleId);
    }

    public VehicleNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}