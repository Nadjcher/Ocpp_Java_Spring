package com.evse.simulator.exception;

/**
 * Exception levée quand une session n'est pas trouvée.
 */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String sessionId) {
        super("Session not found: " + sessionId);
    }

    public SessionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}