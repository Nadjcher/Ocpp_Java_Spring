package com.evse.simulator.controller;

import com.evse.simulator.model.Session;
import com.evse.simulator.ocpp.v16.CancelReservationHandler;
import com.evse.simulator.ocpp.v16.ReserveNowHandler;
import com.evse.simulator.service.ReservationService;
import com.evse.simulator.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Contrôleur de test pour simuler les messages de réservation OCPP 1.6.
 * Permet de tester ReserveNow et CancelReservation sans CSMS externe.
 */
@RestController
@RequestMapping("/api/test/reservation")
@Tag(name = "Reservation Test", description = "Test des réservations OCPP 1.6")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class ReservationTestController {

    private final SessionService sessionService;
    private final ReservationService reservationService;
    private final ReserveNowHandler reserveNowHandler;
    private final CancelReservationHandler cancelReservationHandler;

    /**
     * Simule un message ReserveNow du CSMS.
     *
     * @param sessionId     ID de la session
     * @param reservationId ID de la réservation
     * @param idTag         Tag RFID autorisé
     * @param durationMin   Durée en minutes (default: 30)
     */
    @PostMapping("/{sessionId}/reserve")
    @Operation(summary = "Simule un ReserveNow du CSMS")
    public ResponseEntity<Map<String, Object>> testReserveNow(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "1") int reservationId,
            @RequestParam(defaultValue = "TEST-TAG-001") String idTag,
            @RequestParam(defaultValue = "30") int durationMin,
            @RequestParam(defaultValue = "1") int connectorId) {

        try {
            Session session = sessionService.getSession(sessionId);

            // Construire le payload comme un vrai ReserveNow
            Map<String, Object> payload = new HashMap<>();
            payload.put("connectorId", connectorId);
            payload.put("reservationId", reservationId);
            payload.put("idTag", idTag);

            // Date d'expiration = maintenant + durée
            LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(durationMin);
            payload.put("expiryDate", expiryDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            log.info("[TEST] Simulating ReserveNow for session {}: {}", sessionId, payload);

            // Appeler le handler
            Map<String, Object> response = reserveNowHandler.handle(session, payload);

            // Ajouter des infos de debug
            response.put("sessionId", sessionId);
            response.put("sessionState", session.getState().getValue());
            response.put("reservationId", session.getReservationId());
            response.put("reservationExpiry", session.getReservationExpiry() != null ?
                    session.getReservationExpiry().toString() : null);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[TEST] ReserveNow failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "Error",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Simule un message CancelReservation du CSMS.
     */
    @PostMapping("/{sessionId}/cancel")
    @Operation(summary = "Simule un CancelReservation du CSMS")
    public ResponseEntity<Map<String, Object>> testCancelReservation(
            @PathVariable String sessionId,
            @RequestParam int reservationId) {

        try {
            Session session = sessionService.getSession(sessionId);

            // Construire le payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("reservationId", reservationId);

            log.info("[TEST] Simulating CancelReservation for session {}: {}", sessionId, payload);

            // Appeler le handler
            Map<String, Object> response = cancelReservationHandler.handle(session, payload);

            // Ajouter des infos de debug
            response.put("sessionId", sessionId);
            response.put("sessionState", session.getState().getValue());
            response.put("reservationId", session.getReservationId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[TEST] CancelReservation failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "Error",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Récupère le statut de réservation d'une session.
     */
    @GetMapping("/{sessionId}/status")
    @Operation(summary = "Récupère le statut de réservation d'une session")
    public ResponseEntity<Map<String, Object>> getReservationStatus(@PathVariable String sessionId) {
        try {
            Session session = sessionService.getSession(sessionId);

            Map<String, Object> status = new HashMap<>();
            status.put("sessionId", sessionId);
            status.put("state", session.getState().getValue());
            status.put("ocppStatus", session.getState().getOcppStatus());
            status.put("hasReservation", session.getReservationId() != null);
            status.put("reservationId", session.getReservationId());
            status.put("reservationExpiry", session.getReservationExpiry() != null ?
                    session.getReservationExpiry().toString() : null);
            status.put("reservedIdTag", session.getIdTag());

            // Temps restant si réservation active
            if (session.getReservationExpiry() != null) {
                long remainingSeconds = java.time.Duration.between(
                        LocalDateTime.now(), session.getReservationExpiry()).getSeconds();
                status.put("remainingSeconds", Math.max(0, remainingSeconds));
                status.put("expired", remainingSeconds <= 0);
            }

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Vérifie si les handlers sont bien chargés.
     */
    @GetMapping("/health")
    @Operation(summary = "Vérifie que les handlers sont chargés")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "reserveNowHandler", reserveNowHandler != null,
                "cancelReservationHandler", cancelReservationHandler != null,
                "reservationService", reservationService != null,
                "reserveNowAction", reserveNowHandler.getAction(),
                "cancelReservationAction", cancelReservationHandler.getAction()
        ));
    }
}
