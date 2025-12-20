package com.evse.simulator.document.mapper;

import com.evse.simulator.document.SessionDocument;
import com.evse.simulator.document.SessionDocument.*;
import com.evse.simulator.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper pour convertir entre Session model et SessionDocument MongoDB.
 */
@Component
public class SessionDocumentMapper {

    /**
     * Convertit un model Session en SessionDocument.
     */
    public SessionDocument toDocument(Session session) {
        if (session == null) {
            return null;
        }

        return SessionDocument.builder()
                // Identifiants
                .id(session.getId())
                .title(session.getTitle())
                .cpId(session.getCpId())
                .url(session.getUrl())
                .wsUrl(session.getUrl())
                .bearerToken(session.getBearerToken())

                // État de connexion
                .state(session.getState())
                .connected(session.isConnected())
                .authorized(session.isAuthorized())
                .charging(session.isCharging())
                .parked(session.isParked())
                .plugged(session.isPlugged())

                // Métriques de charge
                .soc(session.getSoc())
                .targetSoc(session.getTargetSoc())
                .currentPowerKw(session.getCurrentPowerKw())
                .maxPowerKw(session.getMaxPowerKw())
                .energyDeliveredKwh(session.getEnergyDeliveredKwh())
                .meterValue(session.getMeterValue())

                // Paramètres électriques
                .voltage(session.getVoltage())
                .currentA(session.getCurrentA())
                .maxCurrentA(session.getMaxCurrentA())
                .temperature(session.getTemperature())

                // Configuration chargeur
                .chargerType(session.getChargerType())
                .connectorId(session.getConnectorId())
                .phaseType(session.getPhaseType())

                // Smart Charging Profile
                .scpLimitKw(session.getScpLimitKw() != 0 ? session.getScpLimitKw() : null)
                .scpLimitA(session.getScpLimitA() != 0 ? session.getScpLimitA() : null)
                .scpProfileId(session.getScpProfileId())
                .scpPurpose(session.getScpPurpose())
                .scpStackLevel(session.getScpStackLevel() != 0 ? session.getScpStackLevel() : null)
                .scpNextPeriodSeconds(session.getScpNextPeriodSeconds())
                .scpNextLimitKw(session.getScpNextLimitKw())

                // Transaction
                .idTag(session.getIdTag())
                .transactionId(session.getTransactionId())
                .txId(session.getTxId())
                .reservationId(session.getReservationId())

                // Référence véhicule
                .vehicleProfile(session.getVehicleProfile())

                // Configuration OCPP
                .heartbeatInterval(session.getHeartbeatInterval())
                .meterValuesInterval(session.getMeterValuesInterval())
                .vendor(session.getVendor())
                .model(session.getModel())
                .serialNumber(session.getSerialNumber())
                .firmwareVersion(session.getFirmwareVersion())
                .ocppVersion(session.getOcppVersion())
                .bootStatus(session.getBootStatus())

                // Profil de charge actif
                .activeChargingProfile(session.getActiveChargingProfile())

                // Timestamps
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .startTime(session.getStartTime())
                .stopTime(session.getStopTime())
                .lastStateChange(session.getLastStateChange())
                .lastKeepalive(session.getLastKeepalive())
                .lastConnected(session.getLastConnected())
                .lastHeartbeat(session.getLastHeartbeat())

                // Statut
                .status(session.getStatus())
                .reconnectAttempts(session.getReconnectAttempts())
                .voluntaryStop(session.isVoluntaryStop())
                .disconnectReason(session.getDisconnectReason())
                .backgrounded(session.isBackgrounded())

                // Collections imbriquées
                .logs(mapLogs(session.getLogs()))
                .socData(mapChartPoints(session.getSocData()))
                .powerData(mapChartPoints(session.getPowerData()))
                .ocppMessages(mapOcppMessages(session.getOcppMessages()))

                .build();
    }

    /**
     * Convertit un SessionDocument en model Session.
     */
    public Session toModel(SessionDocument doc) {
        if (doc == null) {
            return null;
        }

        return Session.builder()
                // Identifiants
                .id(doc.getId())
                .title(doc.getTitle())
                .cpId(doc.getCpId())
                .url(doc.getUrl() != null ? doc.getUrl() : doc.getWsUrl())
                .bearerToken(doc.getBearerToken())

                // État de connexion
                .state(doc.getState())
                .connected(doc.isConnected())
                .authorized(doc.isAuthorized())
                .charging(doc.isCharging())
                .parked(doc.isParked())
                .plugged(doc.isPlugged())

                // Métriques de charge
                .soc(doc.getSoc())
                .targetSoc(doc.getTargetSoc())
                .currentPowerKw(doc.getCurrentPowerKw())
                .maxPowerKw(doc.getMaxPowerKw())
                .energyDeliveredKwh(doc.getEnergyDeliveredKwh())
                .meterValue((int) doc.getMeterValue())

                // Paramètres électriques
                .voltage(doc.getVoltage())
                .currentA(doc.getCurrentA())
                .maxCurrentA(doc.getMaxCurrentA())
                .temperature(doc.getTemperature())

                // Configuration chargeur
                .chargerType(doc.getChargerType())
                .connectorId(doc.getConnectorId())
                .phaseType(doc.getPhaseType())

                // Smart Charging Profile
                .scpLimitKw(doc.getScpLimitKw() != null ? doc.getScpLimitKw() : 0.0)
                .scpLimitA(doc.getScpLimitA() != null ? doc.getScpLimitA() : 0.0)
                .scpProfileId(doc.getScpProfileId())
                .scpPurpose(doc.getScpPurpose())
                .scpStackLevel(doc.getScpStackLevel() != null ? doc.getScpStackLevel() : 0)
                .scpNextPeriodSeconds(doc.getScpNextPeriodSeconds())
                .scpNextLimitKw(doc.getScpNextLimitKw())

                // Transaction
                .idTag(doc.getIdTag())
                .transactionId(doc.getTransactionId())
                .reservationId(doc.getReservationId())

                // Référence véhicule
                .vehicleProfile(doc.getVehicleProfile())

                // Configuration OCPP
                .heartbeatInterval(doc.getHeartbeatInterval())
                .meterValuesInterval(doc.getMeterValuesInterval())
                .vendor(doc.getVendor())
                .model(doc.getModel())
                .serialNumber(doc.getSerialNumber())
                .firmwareVersion(doc.getFirmwareVersion())
                .ocppVersion(doc.getOcppVersion())
                .bootStatus(doc.getBootStatus())

                // Profil de charge actif
                .activeChargingProfile(doc.getActiveChargingProfile())

                // Timestamps
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .startTime(doc.getStartTime())
                .stopTime(doc.getStopTime())
                .lastStateChange(doc.getLastStateChange())
                .lastKeepalive(doc.getLastKeepalive())
                .lastConnected(doc.getLastConnected())
                .lastHeartbeat(doc.getLastHeartbeat())

                // Statut
                .reconnectAttempts(doc.getReconnectAttempts())
                .voluntaryStop(doc.isVoluntaryStop())
                .disconnectReason(doc.getDisconnectReason())
                .backgrounded(doc.isBackgrounded())

                // Collections
                .logs(mapLogsToModel(doc.getLogs()))
                .socData(mapChartPointsToModel(doc.getSocData()))
                .powerData(mapChartPointsToModel(doc.getPowerData()))
                .ocppMessages(mapOcppMessagesToModel(doc.getOcppMessages()))

                .build();
    }

    /**
     * Met à jour un document existant avec les données d'un model.
     */
    public void updateDocument(SessionDocument doc, Session session) {
        if (doc == null || session == null) {
            return;
        }

        // Ne pas changer l'ID
        doc.setTitle(session.getTitle());
        doc.setCpId(session.getCpId());
        doc.setUrl(session.getUrl());
        doc.setWsUrl(session.getUrl());
        doc.setBearerToken(session.getBearerToken());

        doc.setState(session.getState());
        doc.setConnected(session.isConnected());
        doc.setAuthorized(session.isAuthorized());
        doc.setCharging(session.isCharging());
        doc.setParked(session.isParked());
        doc.setPlugged(session.isPlugged());

        doc.setSoc(session.getSoc());
        doc.setTargetSoc(session.getTargetSoc());
        doc.setCurrentPowerKw(session.getCurrentPowerKw());
        doc.setMaxPowerKw(session.getMaxPowerKw());
        doc.setEnergyDeliveredKwh(session.getEnergyDeliveredKwh());
        doc.setMeterValue(session.getMeterValue());

        doc.setVoltage(session.getVoltage());
        doc.setCurrentA(session.getCurrentA());
        doc.setMaxCurrentA(session.getMaxCurrentA());
        doc.setTemperature(session.getTemperature());

        doc.setChargerType(session.getChargerType());
        doc.setConnectorId(session.getConnectorId());
        doc.setPhaseType(session.getPhaseType());

        doc.setScpLimitKw(session.getScpLimitKw() != 0 ? session.getScpLimitKw() : null);
        doc.setScpLimitA(session.getScpLimitA() != 0 ? session.getScpLimitA() : null);
        doc.setScpProfileId(session.getScpProfileId());
        doc.setScpPurpose(session.getScpPurpose());
        doc.setScpStackLevel(session.getScpStackLevel() != 0 ? session.getScpStackLevel() : null);
        doc.setScpNextPeriodSeconds(session.getScpNextPeriodSeconds());
        doc.setScpNextLimitKw(session.getScpNextLimitKw());

        doc.setIdTag(session.getIdTag());
        doc.setTransactionId(session.getTransactionId());
        doc.setTxId(session.getTxId());
        doc.setReservationId(session.getReservationId());

        doc.setVehicleProfile(session.getVehicleProfile());

        doc.setHeartbeatInterval(session.getHeartbeatInterval());
        doc.setMeterValuesInterval(session.getMeterValuesInterval());
        doc.setVendor(session.getVendor());
        doc.setModel(session.getModel());
        doc.setSerialNumber(session.getSerialNumber());
        doc.setFirmwareVersion(session.getFirmwareVersion());
        doc.setOcppVersion(session.getOcppVersion());
        doc.setBootStatus(session.getBootStatus());

        doc.setActiveChargingProfile(session.getActiveChargingProfile());

        doc.setStartTime(session.getStartTime());
        doc.setStopTime(session.getStopTime());
        doc.setLastStateChange(session.getLastStateChange());
        doc.setLastKeepalive(session.getLastKeepalive());
        doc.setLastConnected(session.getLastConnected());
        doc.setLastHeartbeat(session.getLastHeartbeat());

        doc.setStatus(session.getStatus());
        doc.setReconnectAttempts(session.getReconnectAttempts());
        doc.setVoluntaryStop(session.isVoluntaryStop());
        doc.setDisconnectReason(session.getDisconnectReason());
        doc.setBackgrounded(session.isBackgrounded());

        // Collections - remplacer entièrement
        doc.setLogs(mapLogs(session.getLogs()));
        doc.setSocData(mapChartPoints(session.getSocData()));
        doc.setPowerData(mapChartPoints(session.getPowerData()));
        doc.setOcppMessages(mapOcppMessages(session.getOcppMessages()));
    }

    // =========================================================================
    // Méthodes de mapping des collections
    // =========================================================================

    private List<LogEntryEmbedded> mapLogs(List<LogEntry> logs) {
        if (logs == null) {
            return new ArrayList<>();
        }
        return logs.stream()
                .map(log -> LogEntryEmbedded.builder()
                        .timestamp(log.getTimestamp())
                        .level(log.getLevel() != null ? log.getLevel().name() : "INFO")
                        .category(log.getCategory())
                        .message(log.getMessage())
                        .data(log.getData())
                        .build())
                .collect(Collectors.toList());
    }

    private List<LogEntry> mapLogsToModel(List<LogEntryEmbedded> logs) {
        if (logs == null) {
            return new ArrayList<>();
        }
        return logs.stream()
                .map(log -> LogEntry.builder()
                        .timestamp(log.getTimestamp())
                        .level(log.getLevel() != null ? LogEntry.Level.valueOf(log.getLevel()) : LogEntry.Level.INFO)
                        .category(log.getCategory())
                        .message(log.getMessage())
                        .data(log.getData())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ChartPointEmbedded> mapChartPoints(List<ChartPoint> points) {
        if (points == null) {
            return new ArrayList<>();
        }
        return points.stream()
                .map(point -> ChartPointEmbedded.builder()
                        .timestamp(point.getTimestamp())
                        .value(point.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ChartPoint> mapChartPointsToModel(List<ChartPointEmbedded> points) {
        if (points == null) {
            return new ArrayList<>();
        }
        return points.stream()
                .map(point -> ChartPoint.builder()
                        .timestamp(point.getTimestamp())
                        .value(point.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private List<OcppMessageEmbedded> mapOcppMessages(List<OCPPMessage> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }
        return messages.stream()
                .map(msg -> OcppMessageEmbedded.builder()
                        .timestamp(msg.getTimestamp())
                        .direction(msg.getDirection() != null ? msg.getDirection().name() : "OUTGOING")
                        .action(msg.getActionName() != null ? msg.getActionName() :
                                (msg.getAction() != null ? msg.getAction().getValue() : null))
                        .messageId(msg.getMessageId())
                        .payload(msg.getPayload())
                        .processingTimeMs(msg.getProcessingTimeMs())
                        .build())
                .collect(Collectors.toList());
    }

    private List<OCPPMessage> mapOcppMessagesToModel(List<OcppMessageEmbedded> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }
        return messages.stream()
                .map(msg -> OCPPMessage.builder()
                        .timestamp(msg.getTimestamp())
                        .direction(msg.getDirection() != null ?
                                OCPPMessage.Direction.valueOf(msg.getDirection()) :
                                OCPPMessage.Direction.OUTGOING)
                        .actionName(msg.getAction())
                        .messageId(msg.getMessageId())
                        .payload(msg.getPayload() instanceof java.util.Map ?
                                (java.util.Map<String, Object>) msg.getPayload() : null)
                        .processingTimeMs(msg.getProcessingTimeMs())
                        .build())
                .collect(Collectors.toList());
    }
}
