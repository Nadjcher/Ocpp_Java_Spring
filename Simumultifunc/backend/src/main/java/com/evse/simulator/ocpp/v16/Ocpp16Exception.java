package com.evse.simulator.ocpp.v16;

/**
 * Exception OCPP 1.6 avec codes d'erreur standard.
 */
public class Ocpp16Exception extends RuntimeException {

    // OCPP 1.6 Error Codes
    public static final String NOT_IMPLEMENTED = "NotImplemented";
    public static final String NOT_SUPPORTED = "NotSupported";
    public static final String INTERNAL_ERROR = "InternalError";
    public static final String PROTOCOL_ERROR = "ProtocolError";
    public static final String SECURITY_ERROR = "SecurityError";
    public static final String FORMATION_VIOLATION = "FormationViolation";
    public static final String PROPERTY_CONSTRAINT_VIOLATION = "PropertyConstraintViolation";
    public static final String OCCURRENCE_CONSTRAINT_VIOLATION = "OccurrenceConstraintViolation";
    public static final String TYPE_CONSTRAINT_VIOLATION = "TypeConstraintViolation";
    public static final String GENERIC_ERROR = "GenericError";

    private final String errorCode;
    private final String errorDescription;

    public Ocpp16Exception(String message) {
        super(message);
        this.errorCode = INTERNAL_ERROR;
        this.errorDescription = message;
    }

    public Ocpp16Exception(String errorCode, String errorDescription) {
        super(errorDescription);
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }

    public Ocpp16Exception(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = INTERNAL_ERROR;
        this.errorDescription = message;
    }

    public Ocpp16Exception(String errorCode, String errorDescription, Throwable cause) {
        super(errorDescription, cause);
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    // Factory methods
    public static Ocpp16Exception missingField(String fieldName) {
        return new Ocpp16Exception(OCCURRENCE_CONSTRAINT_VIOLATION,
            "Missing required field: " + fieldName);
    }

    public static Ocpp16Exception invalidType(String fieldName, String expectedType) {
        return new Ocpp16Exception(TYPE_CONSTRAINT_VIOLATION,
            "Invalid type for field " + fieldName + ", expected: " + expectedType);
    }

    public static Ocpp16Exception outOfRange(String fieldName, Integer value, int min, int max) {
        return new Ocpp16Exception(PROPERTY_CONSTRAINT_VIOLATION,
            String.format("Field %s value %d is out of range [%d, %d]", fieldName, value, min, max));
    }

    public static Ocpp16Exception propertyConstraintViolation(String message) {
        return new Ocpp16Exception(PROPERTY_CONSTRAINT_VIOLATION, message);
    }

    public static Ocpp16Exception formationViolation(String message) {
        return new Ocpp16Exception(FORMATION_VIOLATION, message);
    }

    public static Ocpp16Exception notSupported(String feature) {
        return new Ocpp16Exception(NOT_SUPPORTED, "Feature not supported: " + feature);
    }

    public static Ocpp16Exception notImplemented(String feature) {
        return new Ocpp16Exception(NOT_IMPLEMENTED, "Feature not implemented: " + feature);
    }
}
