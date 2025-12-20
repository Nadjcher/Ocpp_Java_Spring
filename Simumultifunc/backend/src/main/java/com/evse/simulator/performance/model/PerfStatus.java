package com.evse.simulator.performance.model;

/**
 * Statut d'un test de performance.
 */
public enum PerfStatus {
    IDLE,
    PENDING,
    INITIALIZING,
    RUNNING,
    RAMPING_UP,
    STEADY_STATE,
    RAMPING_DOWN,
    COMPLETED,
    STOPPED,
    FAILED,
    CANCELLED
}
