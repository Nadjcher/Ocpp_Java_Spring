package com.evse.simulator.ocpp.v16.handlers.remote;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.ChargingProfile;
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
 * Handler pour RemoteStartTransaction (CS → CP).
 * <p>
 * Traite les demandes de démarrage distant de transaction envoyées par le CSMS.
 * </p>
 *
 * <h3>Flux OCPP 1.6:</h3>
 * <pre>
 * 1. CSMS envoie RemoteStartTransaction.req avec idTag et optionnellement connectorId/chargingProfile
 * 2. CP répond immédiatement Accepted/Rejected
 * 3. Si Accepted, CP démarre la séquence:
 *    - StatusNotification(Preparing)
 *    - Authorize(idTag) si nécessaire
 *    - StartTransaction
 *    - StatusNotification(Charging)
 * </pre>
 */
@Slf4j
@Component
public class RemoteStartTransactionHandler extends AbstractOcpp16IncomingHandler {

    private final OCPPService ocppService;
    private final ChargingProfileManager chargingProfileManager;
    private final SessionStateManager stateManager;

    public RemoteStartTransactionHandler(@Lazy OCPPService ocppService,
                                         ChargingProfileManager chargingProfileManager,
                                         SessionStateManager stateManager) {
        this.ocppService = ocppService;
        this.chargingProfileManager = chargingProfileManager;
        this.stateManager = stateManager;
    }

    @Override
    public OCPPAction getAction() {
        return OCPPAction.REMOTE_START_TRANSACTION;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);

        // idTag est requis
        String idTag = getString(payload, "idTag", true);
        validateStringLength(idTag, "idTag", 20);

        // connectorId est optionnel mais doit être >= 0 si présent
        Integer connectorId = getInteger(payload, "connectorId", false);
        if (connectorId != null) {
            validateRange(connectorId, "connectorId", 0, 100);
        }
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        try {
            // 1. Extraire les paramètres
            String idTag = getString(payload, "idTag", true);
            Integer connectorId = getInteger(payload, "connectorId", false);
            Map<String, Object> chargingProfileData = getObject(payload, "chargingProfile", false);

            // 2. Valider et déterminer le status
            RemoteStartStopStatus status = validateAndPrepare(session, connectorId, idTag);

            if (status == RemoteStartStopStatus.ACCEPTED) {
                // 3. Stocker l'idTag
                session.setIdTag(idTag);

                // 4. Appliquer le ChargingProfile si fourni
                if (chargingProfileData != null) {
                    applyChargingProfile(session, connectorId, chargingProfileData);
                }

                // 5. Déclencher la séquence de démarrage (async)
                executeRemoteStartSequence(session, connectorId, idTag);

                logToSession(session, String.format(
                    "RemoteStartTransaction ACCEPTED - idTag: %s, connector: %s",
                    idTag, connectorId != null ? connectorId : "auto"
                ));
            } else {
                logToSession(session, String.format(
                    "RemoteStartTransaction REJECTED - idTag: %s, reason: état non compatible",
                    idTag
                ));
            }

            // 6. Construire et retourner la réponse
            Map<String, Object> response = createResponse(status);
            logExit(session, response);
            return response;

        } catch (Ocpp16Exception e) {
            log.error("[{}] RemoteStartTransaction validation error: {}", session.getId(), e.getMessage());
            logErrorToSession(session, "RemoteStartTransaction error: " + e.getMessage());
            return createResponse(RemoteStartStopStatus.REJECTED);
        } catch (Exception e) {
            log.error("[{}] RemoteStartTransaction unexpected error: {}", session.getId(), e.getMessage(), e);
            logErrorToSession(session, "RemoteStartTransaction error: " + e.getMessage());
            return createResponse(RemoteStartStopStatus.REJECTED);
        }
    }

    /**
     * Valide l'état du connecteur et prépare le démarrage.
     */
    private RemoteStartStopStatus validateAndPrepare(Session session, Integer connectorId, String idTag) {
        // Vérifier que la session est connectée
        if (!session.isConnected()) {
            log.warn("[{}] RemoteStart rejected: session not connected", session.getId());
            return RemoteStartStopStatus.REJECTED;
        }

        // Vérifier l'état actuel de la session
        SessionState currentState = session.getState();

        // Si connecteur spécifié, vérifier qu'il est valide
        if (connectorId != null && connectorId > 0) {
            // Pour ce simulateur simple avec 1 connecteur
            if (connectorId > 1) {
                log.warn("[{}] RemoteStart rejected: connector {} does not exist",
                        session.getId(), connectorId);
                return RemoteStartStopStatus.REJECTED;
            }
        }

        // Vérifier que le connecteur est disponible
        if (!isConnectorAvailable(currentState)) {
            log.warn("[{}] RemoteStart rejected: connector not available (state={})",
                    session.getId(), currentState);
            return RemoteStartStopStatus.REJECTED;
        }

        // Vérifier qu'il n'y a pas déjà une transaction en cours
        if (session.getTransactionId() != null && !session.getTransactionId().isEmpty()) {
            log.warn("[{}] RemoteStart rejected: transaction already in progress (txId={})",
                    session.getId(), session.getTransactionId());
            return RemoteStartStopStatus.REJECTED;
        }

        // Valider l'idTag
        if (idTag == null || idTag.trim().isEmpty()) {
            log.warn("[{}] RemoteStart rejected: invalid idTag", session.getId());
            return RemoteStartStopStatus.REJECTED;
        }

        // Si le connecteur est réservé, vérifier que l'idTag correspond
        if (currentState == SessionState.RESERVED) {
            String reservedIdTag = session.getIdTag();
            if (reservedIdTag != null && !reservedIdTag.equals(idTag)) {
                log.warn("[{}] RemoteStart rejected: idTag '{}' does not match reservation idTag '{}'",
                        session.getId(), idTag, reservedIdTag);
                return RemoteStartStopStatus.REJECTED;
            }
            log.info("[{}] RemoteStart: using reservation for idTag={}", session.getId(), idTag);
            // La réservation sera consommée lors du démarrage
            session.setReservationId(null);
            session.setReservationExpiry(null);
        }

        log.info("[{}] RemoteStart validated: idTag={}, connector={}",
                session.getId(), idTag, connectorId);
        return RemoteStartStopStatus.ACCEPTED;
    }

    /**
     * Vérifie si le connecteur est disponible pour démarrer une charge.
     */
    private boolean isConnectorAvailable(SessionState state) {
        return state == SessionState.AVAILABLE ||
               state == SessionState.PREPARING ||
               state == SessionState.CONNECTED ||
               state == SessionState.BOOT_ACCEPTED ||
               state == SessionState.PARKED ||
               state == SessionState.PLUGGED ||
               state == SessionState.RESERVED; // Réservation peut être utilisée par RemoteStart
    }

    /**
     * Applique le ChargingProfile fourni.
     */
    private void applyChargingProfile(Session session, Integer connectorId,
                                      Map<String, Object> profileData) {
        try {
            // Parser et appliquer le profil via ChargingProfileManager
            ChargingProfile profile = parseChargingProfile(profileData);
            if (profile != null) {
                int connector = connectorId != null ? connectorId : session.getConnectorId();
                chargingProfileManager.setChargingProfile(session.getId(), connector, profile);

                log.info("[{}] RemoteStart: ChargingProfile {} applied",
                        session.getId(), profile.getChargingProfileId());
                session.addLog(LogEntry.info("SCP",
                        "ChargingProfile #" + profile.getChargingProfileId() + " applied via RemoteStart"));
            }
        } catch (Exception e) {
            log.warn("[{}] RemoteStart: Failed to apply ChargingProfile: {}",
                    session.getId(), e.getMessage());
            // Ne pas rejeter la transaction à cause d'un profil invalide
        }
    }

    /**
     * Parse un ChargingProfile depuis les données OCPP.
     */
    @SuppressWarnings("unchecked")
    private ChargingProfile parseChargingProfile(Map<String, Object> data) {
        try {
            ChargingProfile.ChargingProfileBuilder builder = ChargingProfile.builder();

            // Champs obligatoires
            Number profileId = (Number) data.get("chargingProfileId");
            Number stackLevel = (Number) data.get("stackLevel");

            if (profileId != null) {
                builder.chargingProfileId(profileId.intValue());
            }
            if (stackLevel != null) {
                builder.stackLevel(stackLevel.intValue());
            }

            // Purpose
            String purpose = (String) data.get("chargingProfilePurpose");
            if (purpose != null) {
                builder.chargingProfilePurpose(
                    ChargingProfile.ChargingProfilePurpose.fromValue(purpose));
            }

            // Kind
            String kind = (String) data.get("chargingProfileKind");
            if (kind != null) {
                builder.chargingProfileKind(
                    ChargingProfile.ChargingProfileKind.fromValue(kind));
            }

            // TransactionId (optionnel)
            Number transactionId = (Number) data.get("transactionId");
            if (transactionId != null) {
                builder.transactionId(transactionId.intValue());
            }

            // ChargingSchedule
            Map<String, Object> scheduleData = (Map<String, Object>) data.get("chargingSchedule");
            if (scheduleData != null) {
                ChargingProfile.ChargingSchedule schedule = parseChargingSchedule(scheduleData);
                builder.chargingSchedule(schedule);
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Failed to parse ChargingProfile: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse un ChargingSchedule.
     */
    @SuppressWarnings("unchecked")
    private ChargingProfile.ChargingSchedule parseChargingSchedule(Map<String, Object> data) {
        ChargingProfile.ChargingSchedule.ChargingScheduleBuilder builder =
            ChargingProfile.ChargingSchedule.builder();

        // ChargingRateUnit
        String rateUnit = (String) data.get("chargingRateUnit");
        if (rateUnit != null) {
            builder.chargingRateUnit(
                ChargingProfile.ChargingRateUnit.fromValue(rateUnit));
        }

        // Duration
        Number duration = (Number) data.get("duration");
        if (duration != null) {
            builder.duration(duration.intValue());
        }

        // ChargingSchedulePeriod
        java.util.List<Map<String, Object>> periodsData =
            (java.util.List<Map<String, Object>>) data.get("chargingSchedulePeriod");
        if (periodsData != null) {
            java.util.List<ChargingProfile.ChargingSchedulePeriod> periods =
                new java.util.ArrayList<>();
            for (Map<String, Object> periodData : periodsData) {
                ChargingProfile.ChargingSchedulePeriod.ChargingSchedulePeriodBuilder periodBuilder =
                    ChargingProfile.ChargingSchedulePeriod.builder();

                Number startPeriod = (Number) periodData.get("startPeriod");
                Number limit = (Number) periodData.get("limit");
                Number numberPhases = (Number) periodData.get("numberPhases");

                if (startPeriod != null) {
                    periodBuilder.startPeriod(startPeriod.intValue());
                }
                if (limit != null) {
                    periodBuilder.limit(limit.doubleValue());
                }
                if (numberPhases != null) {
                    periodBuilder.numberPhases(numberPhases.intValue());
                }

                periods.add(periodBuilder.build());
            }
            builder.chargingSchedulePeriod(periods);
        }

        return builder.build();
    }

    /**
     * Exécute la séquence de démarrage distant de manière asynchrone.
     */
    private void executeRemoteStartSequence(Session session, Integer connectorId, String idTag) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("[{}] RemoteStart: Starting sequence for idTag={}",
                        session.getId(), idTag);

                // Petit délai pour permettre l'envoi de la réponse
                Thread.sleep(200);

                // 1. Passer en Preparing (utiliser forceTransition car venant du CSMS)
                stateManager.forceTransition(session, SessionState.PREPARING, "RemoteStartTransaction from CSMS");
                ocppService.sendStatusNotification(session.getId(), ConnectorStatus.PREPARING);

                log.debug("[{}] RemoteStart: StatusNotification(Preparing) sent", session.getId());

                // 2. Petit délai pour simuler la préparation
                Thread.sleep(300);

                // 3. Envoyer Authorize
                log.debug("[{}] RemoteStart: Sending Authorize for idTag={}",
                        session.getId(), idTag);
                ocppService.sendAuthorize(session.getId());

                // 4. Le flux normal continue via les handlers existants
                // StartTransaction sera envoyé après réception de Authorize.conf

                log.info("[{}] RemoteStart: Sequence initiated successfully", session.getId());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[{}] RemoteStart: Sequence interrupted", session.getId());
            } catch (Exception e) {
                log.error("[{}] RemoteStart: Sequence error: {}",
                        session.getId(), e.getMessage(), e);
                session.addLog(LogEntry.error("OCPP",
                        "RemoteStart sequence failed: " + e.getMessage()));
            }
        });
    }
}
