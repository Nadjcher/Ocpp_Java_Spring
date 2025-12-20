package com.evse.simulator.tnr.model.enums;

/**
 * Catégorie d'événement TNR.
 */
public enum TnrEventCategory {
    OCPP_REQUEST,
    OCPP_RESPONSE,
    OCPP_ERROR,
    HTTP_REQUEST,
    HTTP_RESPONSE,
    WEBSOCKET_OPEN,
    WEBSOCKET_CLOSE,
    WEBSOCKET_ERROR,
    STATE_CHANGE,
    ASSERTION,
    LOG,
    SYSTEM,
    TRANSACTION,
    AUTHENTICATION,
    ERROR,
    CONNECTION
}
