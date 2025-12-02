package com.evse.simulator.domain.service;

import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.OCPPMessage;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.SessionState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface de gestion des sessions de charge.
 */
public interface SessionService {

    // CRUD Operations
    List<Session> getAllSessions();
    Session getSession(String id);
    Optional<Session> findSession(String id);
    Session createSession(Session session);
    Session updateSession(String id, Session updates);
    void deleteSession(String id);

    // State Management
    Session updateState(String id, SessionState state);
    void updateChargingData(String id, double soc, double powerKw, double energyKwh);
    void addLog(String id, LogEntry logEntry);
    void addOcppMessage(String id, OCPPMessage message);

    // Queries
    List<Session> getSessionsByState(SessionState state);
    List<Session> getConnectedSessions();
    List<Session> getChargingSessions();
    long countSessions();
    Map<SessionState, Long> countSessionsByState();

    // Batch Operations
    List<Session> createBatchSessions(int count, Session template);
    int deleteDisconnectedSessions();
}