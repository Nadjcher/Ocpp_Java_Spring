package com.evse.simulator.ocpp.v16.handlers.smartcharging;

import com.evse.simulator.model.ChargingProfile.ChargingRateUnit;
import com.evse.simulator.model.ChargingProfile.ChargingSchedule;
import com.evse.simulator.model.ChargingProfile.ChargingSchedulePeriod;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import com.evse.simulator.ocpp.v16.model.types.GetCompositeScheduleStatus;
import com.evse.simulator.service.SmartChargingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler pour GetCompositeSchedule (CS → CP).
 */
@Slf4j
@Component
public class GetCompositeScheduleHandler extends AbstractOcpp16IncomingHandler {

    private final SmartChargingService smartChargingService;

    public GetCompositeScheduleHandler(SmartChargingService smartChargingService) {
        this.smartChargingService = smartChargingService;
    }

    @Override
    public OCPPAction getAction() {
        return OCPPAction.GET_COMPOSITE_SCHEDULE;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);
        requireField(payload, "connectorId");
        requireField(payload, "duration");
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        Integer connectorId = getInteger(payload, "connectorId", true);
        Integer duration = getInteger(payload, "duration", true);
        String chargingRateUnitStr = getString(payload, "chargingRateUnit", false);

        ChargingRateUnit targetUnit = "A".equalsIgnoreCase(chargingRateUnitStr) ?
                ChargingRateUnit.A : ChargingRateUnit.W;

        Map<String, Object> response = new HashMap<>();

        try {
            ChargingSchedule composite = smartChargingService.getCompositeSchedule(
                    session.getId(),
                    duration,
                    targetUnit
            );

            if (composite != null && composite.getChargingSchedulePeriod() != null
                    && !composite.getChargingSchedulePeriod().isEmpty()) {

                response.put("status", GetCompositeScheduleStatus.ACCEPTED.getValue());
                response.put("connectorId", connectorId);
                response.put("scheduleStart", Instant.now().toString());

                Map<String, Object> schedule = new HashMap<>();
                schedule.put("duration", composite.getDuration());
                schedule.put("chargingRateUnit", composite.getChargingRateUnit().getValue());

                List<Map<String, Object>> periods = new ArrayList<>();
                for (ChargingSchedulePeriod period : composite.getChargingSchedulePeriod()) {
                    Map<String, Object> p = new HashMap<>();
                    p.put("startPeriod", period.getStartPeriod());
                    p.put("limit", period.getLimit());
                    if (period.getNumberPhases() != null) {
                        p.put("numberPhases", period.getNumberPhases());
                    }
                    periods.add(p);
                }
                schedule.put("chargingSchedulePeriod", periods);

                response.put("chargingSchedule", schedule);

                logToSession(session, String.format(
                        "GetCompositeSchedule ACCEPTED - %d periods", periods.size()));

                log.info("[{}] GetCompositeSchedule: connector={}, duration={}, periods={}",
                        session.getId(), connectorId, duration, periods.size());
            } else {
                // Pas de profils actifs
                response.put("status", GetCompositeScheduleStatus.ACCEPTED.getValue());
                response.put("connectorId", connectorId);
                logToSession(session, "GetCompositeSchedule ACCEPTED - no active profiles");
                log.info("[{}] GetCompositeSchedule: connector={}, duration={}, no active profiles",
                        session.getId(), connectorId, duration);
            }

        } catch (Exception e) {
            log.error("[{}] GetCompositeSchedule error: {}", session.getId(), e.getMessage(), e);
            response.put("status", GetCompositeScheduleStatus.REJECTED.getValue());
            logToSession(session, "GetCompositeSchedule REJECTED - " + e.getMessage());
        }

        logExit(session, response);
        return response;
    }
}
