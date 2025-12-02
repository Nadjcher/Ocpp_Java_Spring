package com.evse.simulator.domain.service;

import com.evse.simulator.model.*;

import java.util.List;

/**
 * Interface de diffusion WebSocket temps reel.
 */
public interface BroadcastService {

    // Session Broadcasts
    void broadcastSession(Session session);
    void broadcastAllSessions(List<Session> sessions);

    // Session Details
    void broadcastLog(String sessionId, LogEntry log);
    void broadcastOcppMessage(String sessionId, OCPPMessage message);
    void broadcastChartData(String sessionId, ChartData chartData);

    // Metrics
    void broadcastMetrics(PerformanceMetrics metrics);
}