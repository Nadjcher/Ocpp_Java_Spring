package com.evse.simulator.tnr.model.enums;

/**
 * Type de step dans un scénario TNR.
 */
public enum StepType {
    SEND_REQUEST,
    EXPECT_RESPONSE,
    WAIT,
    ASSERT,
    SETUP,
    TEARDOWN,
    LOG
}
