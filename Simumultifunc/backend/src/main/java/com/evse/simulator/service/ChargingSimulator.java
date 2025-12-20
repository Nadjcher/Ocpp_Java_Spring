package com.evse.simulator.service;

import com.evse.simulator.data.VehicleDatabase;
import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.VehicleProfile;
import com.evse.simulator.model.enums.ChargerType;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Simulateur de charge électrique.
 * Calcule l'évolution de la charge en fonction des caractéristiques du véhicule,
 * de la borne et des profils Smart Charging.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChargingSimulator {

    private final SmartChargingService smartChargingService;

    /**
     * Résultat d'un pas de simulation.
     */
    @Data
    @Builder
    public static class ChargingStep {
        private double powerKw;           // Puissance effective en kW
        private double voltage;           // Tension en V
        private double currentA;          // Courant en A
        private double energyWh;          // Énergie délivrée pendant ce pas en Wh
        private double newSoc;            // Nouveau SoC après ce pas
        private int newMeterValueWh;      // Nouvelle valeur du compteur en Wh
        private Duration etaToTarget;     // Temps restant estimé
        private String limitedBy;         // Ce qui limite la puissance (vehicle, evse, scp)
        private double scpLimitKw;        // Limite Smart Charging si applicable
        private boolean chargingComplete; // True si targetSoc atteint
    }

    /**
     * Simule un pas de charge.
     *
     * @param session session de charge
     * @param intervalSeconds durée du pas en secondes
     * @return résultat du pas de simulation
     */
    public ChargingStep simulate(Session session, int intervalSeconds) {
        // Récupérer le profil véhicule
        VehicleProfile vehicle = VehicleDatabase.getById(session.getVehicleProfile());
        ChargerType chargerType = session.getChargerType();

        // Calculer la puissance effective
        PowerCalculation powerCalc = calculateEffectivePower(session, vehicle, chargerType);

        // Calculer l'énergie délivrée pendant ce pas
        double energyWh = (powerCalc.effectivePowerKw * intervalSeconds / 3.6);

        // Calculer le nouveau SoC
        double energyKwh = energyWh / 1000.0;
        double socIncrease = (energyKwh / vehicle.getBatteryCapacityKwh()) * 100.0;
        double newSoc = Math.min(session.getSoc() + socIncrease, session.getTargetSoc());

        // Vérifier si la charge est terminée
        boolean chargingComplete = newSoc >= session.getTargetSoc();
        if (chargingComplete) {
            newSoc = session.getTargetSoc();
            energyWh = (newSoc - session.getSoc()) / 100.0 * vehicle.getBatteryCapacityKwh() * 1000.0;
        }

        // Calculer le nouveau compteur
        int newMeterValueWh = session.getMeterValue() + (int) energyWh;

        // Calculer l'ETA
        Duration eta = calculateEta(session, vehicle, powerCalc.effectivePowerKw, newSoc);

        // Calculer les valeurs électriques
        double voltage = calculateVoltage(chargerType, vehicle, (int) newSoc);
        double currentA = calculateCurrent(powerCalc.effectivePowerKw, voltage, chargerType);

        return ChargingStep.builder()
                .powerKw(Math.round(powerCalc.effectivePowerKw * 100.0) / 100.0)
                .voltage(Math.round(voltage * 10.0) / 10.0)
                .currentA(Math.round(currentA * 10.0) / 10.0)
                .energyWh(Math.round(energyWh * 10.0) / 10.0)
                .newSoc(Math.round(newSoc * 100.0) / 100.0)
                .newMeterValueWh(newMeterValueWh)
                .etaToTarget(eta)
                .limitedBy(powerCalc.limitedBy)
                .scpLimitKw(powerCalc.scpLimitKw)
                .chargingComplete(chargingComplete)
                .build();
    }

    /**
     * Résultat du calcul de puissance.
     */
    @Data
    @Builder
    private static class PowerCalculation {
        double effectivePowerKw;
        String limitedBy;
        double scpLimitKw;
    }

    /**
     * Calcule la puissance effective de charge.
     */
    private PowerCalculation calculateEffectivePower(Session session, VehicleProfile vehicle, ChargerType chargerType) {
        double evsePowerKw = session.getMaxPowerKw();
        double vehiclePowerKw;
        String limitedBy = "none";

        // Calcul selon le type de charge
        if (chargerType.isDC()) {
            // Charge DC: utiliser la courbe DC du véhicule
            vehiclePowerKw = vehicle.getDcPowerAtSoc((int) session.getSoc());
        } else {
            // Charge AC: calculer selon les phases et le courant
            vehiclePowerKw = vehicle.getEffectiveAcPower(
                    chargerType.getPhases(),
                    session.getMaxCurrentA(),
                    session.getVoltage()
            );
        }

        // Déterminer la limite la plus restrictive
        double effectivePower;
        if (vehiclePowerKw <= evsePowerKw) {
            effectivePower = vehiclePowerKw;
            limitedBy = "vehicle";
        } else {
            effectivePower = evsePowerKw;
            limitedBy = "evse";
        }

        // Appliquer la limite Smart Charging si présente
        double scpLimitKw = 0;
        try {
            log.info("[SIM] Session {}: Calling getCurrentLimit...", session.getId());
            double scpLimit = smartChargingService.getCurrentLimit(session.getId());
            log.info("[SIM] Session {}: SCP limit returned = {} kW, current effectivePower = {} kW",
                    session.getId(), scpLimit, effectivePower);
            if (scpLimit < effectivePower) {
                log.info("[SIM] Session {}: APPLYING SCP limit {} kW (was {} kW)",
                        session.getId(), scpLimit, effectivePower);
                effectivePower = scpLimit;
                limitedBy = "scp";
                scpLimitKw = scpLimit;
            } else {
                log.info("[SIM] Session {}: SCP limit {} kW NOT applied (effectivePower {} kW is lower or equal)",
                        session.getId(), scpLimit, effectivePower);
            }
        } catch (Exception e) {
            log.error("[SIM] Session {}: EXCEPTION getting SCP limit: {}", session.getId(), e.getMessage(), e);
        }

        // Appliquer l'efficacité
        double efficiency = chargerType.isDC() ? vehicle.getEfficiencyDc() : vehicle.getEfficiencyAc();
        effectivePower *= efficiency;

        return PowerCalculation.builder()
                .effectivePowerKw(effectivePower)
                .limitedBy(limitedBy)
                .scpLimitKw(scpLimitKw)
                .build();
    }

    /**
     * Calcule la tension selon le type de charge et le SoC.
     */
    private double calculateVoltage(ChargerType chargerType, VehicleProfile vehicle, int soc) {
        if (chargerType.isDC()) {
            return vehicle.getVoltageAtSoc(soc);
        } else {
            // AC: tension du réseau
            return chargerType.getPhases() == 1 ? 230.0 : 400.0;
        }
    }

    /**
     * Calcule le courant par phase à partir de la puissance et de la tension.
     */
    private double calculateCurrent(double powerKw, double voltage, ChargerType chargerType) {
        if (chargerType.isDC()) {
            // DC: I = P / U
            return (powerKw * 1000.0) / voltage;
        } else {
            int phases = chargerType.getPhases();
            if (phases > 1) {
                // Triphasé: déterminer si la tension est phase-neutre ou ligne-ligne
                if (voltage < 300) {
                    // Tension phase-neutre (ex: 230V) - I = P / (V × phases)
                    return (powerKw * 1000.0) / (voltage * phases);
                } else {
                    // Tension ligne-ligne (ex: 400V) - I = P / (V × √3)
                    return (powerKw * 1000.0) / (voltage * Math.sqrt(3));
                }
            } else {
                // Monophasé: I = P / V
                return (powerKw * 1000.0) / voltage;
            }
        }
    }

    /**
     * Calcule le temps restant estimé pour atteindre le SoC cible.
     */
    private Duration calculateEta(Session session, VehicleProfile vehicle, double currentPowerKw, double currentSoc) {
        if (currentSoc >= session.getTargetSoc() || currentPowerKw <= 0) {
            return Duration.ZERO;
        }

        double remainingEnergy = vehicle.getBatteryCapacityKwh() * (session.getTargetSoc() - currentSoc) / 100.0;

        // Estimer la puissance moyenne pour le reste de la charge
        // (la puissance diminue généralement vers la fin)
        double avgPower = estimateAveragePower(session, vehicle, currentSoc, currentPowerKw);

        double hoursRemaining = remainingEnergy / avgPower;
        long minutesRemaining = Math.round(hoursRemaining * 60);

        return Duration.ofMinutes(minutesRemaining);
    }

    /**
     * Estime la puissance moyenne pour le reste de la charge.
     */
    private double estimateAveragePower(Session session, VehicleProfile vehicle, double currentSoc, double currentPowerKw) {
        // Pour une estimation plus précise, on simule quelques points
        double targetSoc = session.getTargetSoc();
        double socRange = targetSoc - currentSoc;

        if (socRange <= 0) return currentPowerKw;

        double totalPower = 0;
        int steps = 10;
        double socStep = socRange / steps;

        for (int i = 0; i < steps; i++) {
            double soc = currentSoc + (i * socStep);
            double power = session.getChargerType().isDC()
                    ? vehicle.getDcPowerAtSoc((int) soc)
                    : vehicle.getMaxAcPowerKw();
            power = Math.min(power, session.getMaxPowerKw());
            totalPower += power;
        }

        return totalPower / steps;
    }

    /**
     * Simule une charge complète et retourne le temps total estimé.
     *
     * @param session session de charge
     * @return durée estimée totale
     */
    public Duration estimateFullCharge(Session session) {
        VehicleProfile vehicle = VehicleDatabase.getById(session.getVehicleProfile());
        double avgPower = estimateAveragePower(session, vehicle, session.getSoc(), session.getMaxPowerKw());

        double energyNeeded = vehicle.getBatteryCapacityKwh() *
                (session.getTargetSoc() - session.getSoc()) / 100.0;

        double hours = energyNeeded / avgPower;
        return Duration.ofMinutes(Math.round(hours * 60));
    }
}
