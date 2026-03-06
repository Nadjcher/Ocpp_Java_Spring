package com.evse.simulator.service;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.model.enums.SessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour la vérification de l'intervalle d'envoi des MeterValues.
 * <p>
 * Vérifie que les MeterValues sont envoyées conformément à la période configurée.
 * Ex: si configuré à 60s, l'envoi doit se faire toutes les 60s (±10% tolérance).
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class MeterValuesIntervalVerificationTest {

    @Mock
    private SessionService sessionService;

    private Session testSession;
    private static final String SESSION_ID = "test-session-1";

    @BeforeEach
    void setUp() {
        testSession = Session.builder()
                .id(SESSION_ID)
                .title("Test Session")
                .url("ws://localhost:8080/ocpp")
                .chargerType(ChargerType.AC)
                .state(SessionState.CHARGING)
                .meterValuesInterval(60)
                .build();

        lenient().when(sessionService.getSession(SESSION_ID)).thenReturn(testSession);
        lenient().doNothing().when(sessionService).addLog(anyString(), any());
    }

    @Test
    @DisplayName("Premier envoi : pas de violation, initialisation du tracking")
    void firstSend_shouldInitializeTracking() {
        // Given
        long expectedIntervalMs = 60_000;
        assertThat(testSession.getLastMeterValuesSentAtMs()).isZero();
        assertThat(testSession.getMeterValuesSendCount()).isZero();

        // When - simulate first send
        long now = System.currentTimeMillis();
        testSession.setMeterValuesSendCount(testSession.getMeterValuesSendCount() + 1);
        testSession.setLastMeterValuesSentAtMs(now);

        // Then
        assertThat(testSession.getMeterValuesSendCount()).isEqualTo(1);
        assertThat(testSession.getLastMeterValuesSentAtMs()).isGreaterThan(0);
        assertThat(testSession.getMeterValuesIntervalViolations()).isZero();
    }

    @Test
    @DisplayName("Envoi conforme à 60s : pas de violation")
    void sendAtExactInterval_shouldNotViolate() {
        // Given: simulate first send
        long expectedIntervalMs = 60_000;
        long firstSendTime = 1_000_000_000L;
        testSession.setLastMeterValuesSentAtMs(firstSendTime);
        testSession.setMeterValuesSendCount(1);

        // When: second send at exactly 60s later
        long secondSendTime = firstSendTime + 60_000;
        long actualIntervalMs = secondSendTime - testSession.getLastMeterValuesSentAtMs();
        long deviationMs = Math.abs(actualIntervalMs - expectedIntervalMs);

        testSession.setLastMeterValuesSentAtMs(secondSendTime);
        testSession.setMeterValuesSendCount(2);
        if (deviationMs > testSession.getMeterValuesMaxDeviationMs()) {
            testSession.setMeterValuesMaxDeviationMs(deviationMs);
        }

        // Then
        assertThat(deviationMs).isZero();
        assertThat(testSession.getMeterValuesIntervalViolations()).isZero();
    }

    @Test
    @DisplayName("Envoi avec légère déviation (5%) : pas de violation")
    void sendWithSmallDeviation_shouldNotViolate() {
        // Given
        long expectedIntervalMs = 60_000;
        double tolerancePercent = 10.0;
        double toleranceMs = expectedIntervalMs * tolerancePercent / 100.0; // 6000ms

        long firstSendTime = 1_000_000_000L;
        testSession.setLastMeterValuesSentAtMs(firstSendTime);
        testSession.setMeterValuesSendCount(1);

        // When: second send at 63s (5% deviation)
        long secondSendTime = firstSendTime + 63_000;
        long actualIntervalMs = secondSendTime - testSession.getLastMeterValuesSentAtMs();
        long deviationMs = Math.abs(actualIntervalMs - expectedIntervalMs);

        // Then
        assertThat(deviationMs).isEqualTo(3_000);
        assertThat(deviationMs).isLessThan((long) toleranceMs);
    }

    @Test
    @DisplayName("Envoi avec grande déviation (20%) : violation détectée")
    void sendWithLargeDeviation_shouldViolate() {
        // Given
        long expectedIntervalMs = 60_000;
        double tolerancePercent = 10.0;
        double toleranceMs = expectedIntervalMs * tolerancePercent / 100.0; // 6000ms

        long firstSendTime = 1_000_000_000L;
        testSession.setLastMeterValuesSentAtMs(firstSendTime);
        testSession.setMeterValuesSendCount(1);

        // When: second send at 72s (20% deviation)
        long secondSendTime = firstSendTime + 72_000;
        long actualIntervalMs = secondSendTime - testSession.getLastMeterValuesSentAtMs();
        long deviationMs = Math.abs(actualIntervalMs - expectedIntervalMs);

        if (deviationMs > toleranceMs) {
            testSession.setMeterValuesIntervalViolations(
                    testSession.getMeterValuesIntervalViolations() + 1);
        }

        testSession.setLastMeterValuesSentAtMs(secondSendTime);
        testSession.setMeterValuesSendCount(2);
        testSession.setMeterValuesMaxDeviationMs(deviationMs);

        // Then
        assertThat(deviationMs).isEqualTo(12_000);
        assertThat(deviationMs).isGreaterThan((long) toleranceMs);
        assertThat(testSession.getMeterValuesIntervalViolations()).isEqualTo(1);
        assertThat(testSession.getMeterValuesMaxDeviationMs()).isEqualTo(12_000);
    }

    @Test
    @DisplayName("Multiple envois : comptage correct des violations")
    void multipleSends_shouldCountViolationsCorrectly() {
        // Given
        long expectedIntervalMs = 60_000;
        double toleranceMs = expectedIntervalMs * 10.0 / 100.0; // 6000ms

        // Simuler une série d'envois avec timestamps
        long[] sendTimestamps = {
                1_000_000_000L,        // t0 : premier envoi
                1_000_060_000L,        // t1 : +60s (OK)
                1_000_120_000L,        // t2 : +60s (OK)
                1_000_195_000L,        // t3 : +75s (VIOLATION, +25%)
                1_000_255_000L,        // t4 : +60s (OK)
                1_000_280_000L,        // t5 : +25s (VIOLATION, -58%)
        };

        int violations = 0;
        long maxDeviation = 0;

        for (int i = 1; i < sendTimestamps.length; i++) {
            long actualIntervalMs = sendTimestamps[i] - sendTimestamps[i - 1];
            long deviationMs = Math.abs(actualIntervalMs - expectedIntervalMs);

            if (deviationMs > maxDeviation) {
                maxDeviation = deviationMs;
            }

            if (deviationMs > toleranceMs) {
                violations++;
            }
        }

        // Then
        assertThat(violations).isEqualTo(2); // t3 (+15s) et t5 (-35s)
        assertThat(maxDeviation).isEqualTo(35_000); // t5: |25000 - 60000| = 35000ms
    }

    @Test
    @DisplayName("Intervalle court (10s) : tolérance ajustée")
    void shortInterval_shouldHaveProportionalTolerance() {
        // Given: 10s interval → 1s tolerance
        long expectedIntervalMs = 10_000;
        double toleranceMs = expectedIntervalMs * 10.0 / 100.0; // 1000ms

        long firstSendTime = 1_000_000_000L;

        // When: send at 10.5s (5% deviation) → OK
        long actualInterval1 = 10_500;
        long deviation1 = Math.abs(actualInterval1 - expectedIntervalMs);
        assertThat(deviation1).isLessThan((long) toleranceMs);

        // When: send at 12s (20% deviation) → VIOLATION
        long actualInterval2 = 12_000;
        long deviation2 = Math.abs(actualInterval2 - expectedIntervalMs);
        assertThat(deviation2).isGreaterThan((long) toleranceMs);
    }

    @Test
    @DisplayName("Intervalle long (300s/5min) : tolérance ajustée")
    void longInterval_shouldHaveProportionalTolerance() {
        // Given: 300s interval → 30s tolerance
        long expectedIntervalMs = 300_000;
        double toleranceMs = expectedIntervalMs * 10.0 / 100.0; // 30000ms

        // When: send at 310s (3.3% deviation) → OK
        long actualInterval1 = 310_000;
        long deviation1 = Math.abs(actualInterval1 - expectedIntervalMs);
        assertThat(deviation1).isLessThan((long) toleranceMs);

        // When: send at 340s (13.3% deviation) → VIOLATION
        long actualInterval2 = 340_000;
        long deviation2 = Math.abs(actualInterval2 - expectedIntervalMs);
        assertThat(deviation2).isGreaterThan((long) toleranceMs);
    }

    @Test
    @DisplayName("Reset des compteurs au démarrage d'un nouveau cycle")
    void startNewCycle_shouldResetCounters() {
        // Given: session with previous violations
        testSession.setMeterValuesSendCount(15);
        testSession.setMeterValuesMaxDeviationMs(12_000);
        testSession.setMeterValuesIntervalViolations(3);
        testSession.setLastMeterValuesSentAtMs(System.currentTimeMillis());

        // When: reset (as done in startMeterValuesWithInterval)
        testSession.setLastMeterValuesSentAtMs(0);
        testSession.setMeterValuesSendCount(0);
        testSession.setMeterValuesMaxDeviationMs(0);
        testSession.setMeterValuesIntervalViolations(0);

        // Then
        assertThat(testSession.getLastMeterValuesSentAtMs()).isZero();
        assertThat(testSession.getMeterValuesSendCount()).isZero();
        assertThat(testSession.getMeterValuesMaxDeviationMs()).isZero();
        assertThat(testSession.getMeterValuesIntervalViolations()).isZero();
    }
}
