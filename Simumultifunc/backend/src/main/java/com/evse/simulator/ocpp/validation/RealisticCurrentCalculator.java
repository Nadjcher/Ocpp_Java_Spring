package com.evse.simulator.ocpp.validation;

import com.evse.simulator.data.VehicleDatabase;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.VehicleProfile;
import com.evse.simulator.model.enums.ChargerType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Calculateur de courant réaliste pour les MeterValues.
 *
 * Logique:
 * 1. Charge active: utilise le courant configuré (phasing), limité par le véhicule
 * 2. SoC élevé (>80%): réduit progressivement le courant (courbe de charge réaliste)
 * 3. SoC = target ou charge terminée: courant ~0A (veille)
 * 4. Pas de transaction active: courant ~0A
 */
@Component
@Slf4j
public class RealisticCurrentCalculator {

    // Courant de veille (mA) - légère consommation des systèmes embarqués
    private static final double IDLE_CURRENT_A = 0.025;

    // Seuil de SoC pour commencer la réduction de courant (phase CC->CV)
    private static final double SOC_TAPER_START = 80.0;

    /**
     * Résultat du calcul de courant avec métadonnées.
     */
    public static class CurrentResult {
        private final double currentA;
        private final String reason;
        private final boolean isCharging;

        public CurrentResult(double currentA, String reason, boolean isCharging) {
            this.currentA = currentA;
            this.reason = reason;
            this.isCharging = isCharging;
        }

        public double getCurrentA() { return currentA; }
        public String getReason() { return reason; }
        public boolean isCharging() { return isCharging; }
    }

    /**
     * Calcule le courant réaliste pour une session de charge.
     *
     * @param session la session de charge
     * @return résultat avec courant et raison
     */
    public CurrentResult calculateRealisticCurrent(Session session) {
        if (session == null) {
            return new CurrentResult(0, "no_session", false);
        }

        // 1. Vérifier si une transaction est active
        String transactionId = session.getTransactionId();
        if (transactionId == null || transactionId.isEmpty()) {
            return new CurrentResult(IDLE_CURRENT_A, "no_transaction", false);
        }

        // 2. Vérifier si la charge est terminée (SoC >= targetSoc)
        double soc = session.getSoc();
        double targetSoc = session.getTargetSoc();
        if (soc >= targetSoc) {
            return new CurrentResult(IDLE_CURRENT_A, "soc_target_reached", false);
        }

        // 3. Récupérer les limites
        double configuredCurrentA = session.getMaxCurrentA(); // Courant configuré dans phasing
        ChargerType chargerType = session.getChargerType();
        int phases = session.getEffectivePhases();

        // 4. Récupérer les limites du véhicule
        VehicleProfile vehicle = getVehicleProfile(session);
        double vehicleMaxCurrentA = vehicle != null ? vehicle.getMaxAcCurrentA() : configuredCurrentA;

        // 5. Le courant effectif est le minimum entre config et véhicule
        double effectiveCurrentA = Math.min(configuredCurrentA, vehicleMaxCurrentA);
        String limitedBy = configuredCurrentA <= vehicleMaxCurrentA ? "evse_config" : "vehicle";

        // 6. Appliquer la courbe de réduction (taper) si SoC > 80%
        if (soc > SOC_TAPER_START) {
            effectiveCurrentA = applyTaperCurve(effectiveCurrentA, soc, targetSoc);
            limitedBy = "soc_taper";
        }

        // 7. Vérifier si la puissance actuelle indique une charge active
        double currentPowerKw = session.getCurrentPowerKw();
        if (currentPowerKw <= 0.1) {
            // Pas de puissance = pas de charge active
            return new CurrentResult(IDLE_CURRENT_A, "no_power", false);
        }

        log.debug("[{}] Realistic current: {}A (limited by: {}, soc: {}%, vehicle: {})",
                session.getId(),
                String.format("%.2f", effectiveCurrentA),
                limitedBy,
                String.format("%.1f", soc),
                vehicle != null ? vehicle.getId() : "unknown");

        return new CurrentResult(effectiveCurrentA, limitedBy, true);
    }

    /**
     * Applique la courbe de réduction de courant (phase CC->CV).
     * En charge AC, la puissance diminue progressivement après 80% de SoC.
     *
     * @param baseCurrent courant de base (max)
     * @param currentSoc SoC actuel
     * @param targetSoc SoC cible
     * @return courant ajusté
     */
    private double applyTaperCurve(double baseCurrent, double currentSoc, double targetSoc) {
        // Courbe de réduction réaliste:
        // - 80% SoC: 100% du courant
        // - 90% SoC: ~70% du courant
        // - 95% SoC: ~40% du courant
        // - 100% SoC: ~10% du courant (puis arrêt)

        if (currentSoc >= 100) {
            return IDLE_CURRENT_A;
        }

        double socRange = 100 - SOC_TAPER_START; // 20%
        double socProgress = (currentSoc - SOC_TAPER_START) / socRange; // 0 à 1

        // Courbe exponentielle décroissante
        // À 80%: factor = 1.0
        // À 90%: factor ≈ 0.7
        // À 100%: factor ≈ 0.1
        double factor = Math.pow(1 - socProgress, 1.5);
        factor = Math.max(factor, 0.1); // Minimum 10%

        return baseCurrent * factor;
    }

    /**
     * Récupère le profil véhicule depuis la session.
     */
    private VehicleProfile getVehicleProfile(Session session) {
        String vehicleId = session.getVehicleProfile();
        if (vehicleId == null || vehicleId.isEmpty()) {
            return null;
        }
        try {
            return VehicleDatabase.getById(vehicleId);
        } catch (Exception e) {
            log.warn("Vehicle profile not found: {}", vehicleId);
            return null;
        }
    }

    /**
     * Calcule la puissance correspondant à un courant donné.
     *
     * @param currentA courant en A
     * @param voltage tension en V
     * @param phases nombre de phases
     * @param chargerType type de chargeur
     * @return puissance en W
     */
    public double calculatePowerFromCurrent(double currentA, double voltage, int phases, ChargerType chargerType) {
        if (chargerType != null && chargerType.isDC()) {
            return currentA * voltage;
        }

        if (phases >= 3) {
            // Triphasé:
            // - Tension phase-neutre (230V): P = phases × V × I = 3 × V × I
            // - Tension ligne-ligne (400V): P = √3 × V × I
            if (voltage > 300) {
                // Tension ligne-ligne (400V)
                return Math.sqrt(3) * voltage * currentA;
            } else {
                // Tension phase-neutre (230V)
                return phases * voltage * currentA;
            }
        } else if (phases == 2) {
            return 2 * voltage * currentA;
        } else {
            return voltage * currentA;
        }
    }
}
