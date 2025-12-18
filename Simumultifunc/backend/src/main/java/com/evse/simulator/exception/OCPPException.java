package com.evse.simulator.exception;

import lombok.Getter;

/**
 * Exception pour les erreurs OCPP.
 */
@Getter
public class OCPPException extends RuntimeException {

    private final String errorCode;
    private final String sessionId;

    public OCPPException(String message) {
        super(message);
        this.errorCode = "GenericError";
        this.sessionId = null;
    }

    public OCPPException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.sessionId = null;
    }

    public OCPPException(String errorCode, String message, String sessionId) {
        super(message);
        this.errorCode = errorCode;
        this.sessionId = sessionId;
    }

    public OCPPException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "InternalError";
        this.sessionId = null;
    }

    /**
     * Codes d'erreur OCPP standards.
     */
    public static class ErrorCode {
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
    }
}