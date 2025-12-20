package com.evse.simulator.ocpp.v16.handlers.remote;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ConnectorStatus;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import com.evse.simulator.ocpp.v16.model.types.RemoteStartStopStatus;
import com.evse.simulator.service.ChargingProfileManager;
import com.evse.simulator.service.SessionStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour RemoteStopTransaction (CS → CP).
 * <p>
 * Traite les demandes d'arrêt distant de transaction envoyées par le CSMS.
 * </p>
 *
 * <h3>Flux OCPP 1.6:</h3>
 * <pre>
 * 1. CSMS envoie RemoteStopTransaction.req avec transactionId
 * 2. CP répond immédiatement Accepted/Rejected
 * 3. Si Accepted, CP arrête la charge:
 *    - MeterValues final (optionnel)
 *    - StopTransaction avec reason="Remote"
 *    - StatusNotification(Finishing)
 *    - StatusNotification(Available) après débranchement
 * </pre>
 */
@Slf4j
@Component
public class RemoteStopTransactionHandler extends AbstractOcpp16IncomingHandler {

    private final OCPPService ocppService;
    private final ChargingProfileManager chargingProfileManager;
    private final SessionStateManager stateManager;

    public RemoteStopTransactionHandler(@Lazy OCPPService ocppService,
                                        ChargingProfileManager chargingProfileManager,
                                        SessionStateManager stateManager) {
        this.ocppService = ocppService;
        this.chargingProfileManager = chargingProfileManager;
        this.stateManager = stateManager;
    }

    @Override
    public OCPPAction getAction() {
        return OCPPAction.REMOTE_STOP_TRANSACTION;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);

        // transactionId est requis
        Integer transactionId = getInteger(payload, "transactionId", true);
        if (transactionId == null || transactionId <= 0) {
            throw new Ocpp16Exception("Invalid transactionId",
                    Ocpp16Exception.PROPERTY_CONSTRAINT_VIOLATION);
        }
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        try {
            // 1. Extraire le transactionId
            Integer transactionId = getInteger(payload, "transactionId", true);

            // 2. Valider que la transaction existe et est active
            RemoteStartStopStatus status = validateTransaction(session, transactionId);

            if (status == RemoteStartStopStatus.ACCEPTED) {
                // 3. Déclencher la séquence d'arrêt (async)
                executeRemoteStopSequence(session, transactionId);

                logToSession(session, String.format(
                    "RemoteStopTransaction ACCEPTED - transactionId: %d",
                    transactionId
                ));
            } else {
                logToSession(session, String.format(
                    "RemoteStopTransaction REJECTED - transactionId: %d not found or not active",
                    transactionId
                ));
            }

            // 4. Construire et retourner la réponse
            Map<String, Object> response = createResponse(status);
            logExit(session, response);
            return response;

        } catch (Ocpp16Exception e) {
            log.error("[{}] RemoteStopTransaction validation error: {}", session.getId(), e.getMessage());
            logErrorToSession(session, "RemoteStopTransaction error: " + e.getMessage());
            return createResponse(RemoteStartStopStatus.REJECTED);
        } catch (Exception e) {
            log.error("[{}] RemoteStopTransaction unexpected error: {}", session.getId(), e.getMessage(), e);
            logErrorToSession(session, "RemoteStopTransaction error: " + e.getMessage());
            return createResponse(RemoteStartStopStatus.REJECTED);
        }
    }

    /**
     * Valide que la transaction existe et est active.
     */
    private RemoteStartStopStatus validateTransaction(Session session, Integer transactionId) {
        // Vérifier que la session est connectée
        if (!session.isConnected()) {
            log.warn("[{}] RemoteStop rejected: session not connected", session.getId());
            return RemoteStartStopStatus.REJECTED;
        }

        // Vérifier qu'il y a une transaction en cours
        String currentTxId = session.getTransactionId();
        if (currentTxId == null || currentTxId.isEmpty()) {
            log.warn("[{}] RemoteStop rejected: no active transaction", session.getId());
            return RemoteStartStopStatus.REJECTED;
        }

        // Vérifier que le transactionId correspond
        try {
            int currentTxIdInt = Integer.parseInt(currentTxId);
            if (currentTxIdInt != transactionId) {
                log.warn("[{}] RemoteStop rejected: transactionId mismatch (expected={}, received={})",
                        session.getId(), currentTxId, transactionId);
                return RemoteStartStopStatus.REJECTED;
            }
        } catch (NumberFormatException e) {
            // Si le txId stocké n'est pas un nombre, comparer comme string
            if (!currentTxId.equals(String.valueOf(transactionId))) {
                log.warn("[{}] RemoteStop rejected: transactionId mismatch", session.getId());
                return RemoteStartStopStatus.REJECTED;
            }
        }

        // Vérifier l'état de la session
        SessionState state = session.getState();
        if (!isTransactionActive(state)) {
            log.warn("[{}] RemoteStop rejected: no active charging (state={})",
                    session.getId(), state);
            return RemoteStartStopStatus.REJECTED;
        }

        log.info("[{}] RemoteStop validated: transactionId={}", session.getId(), transactionId);
        return RemoteStartStopStatus.ACCEPTED;
    }

    /**
     * Vérifie si une transaction est active.
     */
    private boolean isTransactionActive(SessionState state) {
        return state == SessionState.CHARGING ||
               state == SessionState.SUSPENDED_EV ||
               state == SessionState.SUSPENDED_EVSE;
    }

    /**
     * Exécute la séquence d'arrêt distant de manière asynchrone.
     */
    private void executeRemoteStopSequence(Session session, Integer transactionId) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("[{}] RemoteStop: Starting stop sequence for transactionId={}",
                        session.getId(), transactionId);

                // Petit délai pour permettre l'envoi de la réponse
                Thread.sleep(200);

                // 1. Envoyer MeterValues final (optionnel mais recommandé)
                try {
                    log.debug("[{}] RemoteStop: Sending final MeterValues", session.getId());
                    ocppService.sendMeterValues(session.getId());
                    Thread.sleep(100);
                } catch (Exception e) {
                    log.warn("[{}] RemoteStop: Failed to send final MeterValues: {}",
                            session.getId(), e.getMessage());
                }

                // 2. Passer en Stopping
                stateManager.forceTransition(session, SessionState.STOPPING, "RemoteStopTransaction from CSMS");

                // 3. Arrêter les MeterValues automatiques
                try {
                    ocppService.stopMeterValuesPublic(session.getId());
                } catch (Exception e) {
                    log.debug("[{}] RemoteStop: MeterValues already stopped", session.getId());
                }

                // 4. Envoyer StopTransaction avec reason="Remote"
                log.debug("[{}] RemoteStop: Sending StopTransaction", session.getId());
                ocppService.sendStopTransaction(session.getId());

                // 5. Passer en Finishing
                Thread.sleep(200);
                stateManager.forceTransition(session, SessionState.FINISHING, "StopTransaction completed");
                ocppService.sendStatusNotification(session.getId(), ConnectorStatus.FINISHING);

                // 6. Nettoyer les profils TxProfile
                cleanupTxProfiles(session);

                // 7. Simuler le passage à Available (après "débranchement")
                Thread.sleep(500);
                stateManager.forceTransition(session, SessionState.AVAILABLE, "Cable unplugged after RemoteStop");
                session.setTransactionId(null);
                session.setCharging(false);
                ocppService.sendStatusNotification(session.getId(), ConnectorStatus.AVAILABLE);

                log.info("[{}] RemoteStop: Sequence completed successfully", session.getId());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[{}] RemoteStop: Sequence interrupted", session.getId());
            } catch (Exception e) {
                log.error("[{}] RemoteStop: Sequence error: {}",
                        session.getId(), e.getMessage(), e);
                session.addLog(LogEntry.error("OCPP",
                        "RemoteStop sequence failed: " + e.getMessage()));

                // Essayer de remettre la session dans un état stable
                try {
                    stateManager.forceTransition(session, SessionState.AVAILABLE, "Recovery after RemoteStop error");
                    session.setCharging(false);
                } catch (Exception ignored) {
                }
            }
        });
    }

    /**
     * Nettoie les profils TxProfile associés à cette transaction.
     */
    private void cleanupTxProfiles(Session session) {
        try {
            // Supprimer les profils TxProfile (purpose = TxProfile)
            boolean removed = chargingProfileManager.clearChargingProfile(
                    session.getId(),
                    null, // tous les profils
                    session.getConnectorId(),
                    "TxProfile",
                    null
            );

            if (removed) {
                log.debug("[{}] RemoteStop: TxProfiles cleared", session.getId());
                session.setScpLimitKw(0);
                session.setScpProfileId(null);
                session.setScpPurpose(null);
            }
        } catch (Exception e) {
            log.debug("[{}] RemoteStop: No TxProfiles to clear", session.getId());
        }
    }
}
