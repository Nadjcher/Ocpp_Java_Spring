package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.model.enums.OCPPAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour le message MeterValues.
 * Envoie les valeurs de compteur pendant une charge.
 * Supporte les mesures monophasées, biphasées et triphasées.
 */
@Component
public class MeterValuesHandler extends AbstractOcppHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.METER_VALUES;
    }

    @Override
    public Map<String, Object> buildPayload(OcppMessageContext context) {
        int connectorId = context.getConnectorId() > 0 ? context.getConnectorId() : 1;
        Integer transactionId = context.getTransactionId();

        List<Map<String, Object>> meterValue = new ArrayList<>();
        meterValue.add(buildMeterValueSample(context));

        Map<String, Object> payload = createPayload(
            "connectorId", connectorId,
            "meterValue", meterValue
        );

        if (transactionId != null) {
            payload.put("transactionId", transactionId);
        }

        return payload;
    }

    /**
     * Construit un échantillon de valeurs de compteur.
     * Génère des valeurs par phase pour AC triphasé/biphasé.
     */
    private Map<String, Object> buildMeterValueSample(OcppMessageContext context) {
        List<Map<String, Object>> sampledValues = new ArrayList<>();

        Session session = context.getSession();
        ChargerType chargerType = session != null ? session.getChargerType() : null;
        // Utiliser les phases effectives (respecte activePhases configuré par phasing ou véhicule)
        int phases = session != null ? session.getEffectivePhases() : 1;

        // Énergie active importée totale (Wh)
        long energyWh = context.getMeterValue() != null ? context.getMeterValue() : 0L;
        sampledValues.add(createSampledValue(
            String.valueOf(energyWh),
            "Energy.Active.Import.Register",
            "Wh",
            "Outlet",
            null
        ));

        if (session != null) {
            double powerW = session.getCurrentPowerKw() * 1000;
            // Utiliser le courant MAX configuré (pas le courant calculé) pour les MeterValues
            double currentA = session.getMaxCurrentA();
            double voltageV = session.getVoltage();

            // Pour AC triphasé ou biphasé, générer des valeurs par phase
            if (chargerType != null && chargerType.isAC() && phases >= 2) {
                // Déterminer si voltageV est la tension phase-neutre ou phase-phase
                // En Europe: phase-neutre ~230V, phase-phase ~400V
                // Si voltageV <= 260V, c'est probablement la tension phase-neutre
                // Si voltageV > 350V, c'est probablement la tension phase-phase
                double phaseVoltage;
                double lineToLineVoltage;
                if (voltageV > 350) {
                    // voltageV est la tension phase-phase (ex: 400V)
                    phaseVoltage = voltageV / Math.sqrt(3);  // ~230V
                    lineToLineVoltage = voltageV;
                } else {
                    // voltageV est la tension phase-neutre (ex: 230V)
                    phaseVoltage = voltageV;  // ~230V directement
                    lineToLineVoltage = voltageV * Math.sqrt(3);  // ~400V
                }

                // Répartition réaliste de la puissance et de l'énergie par phase
                // En triphasé équilibré, la puissance et l'énergie sont réparties sur les phases
                double[] phasePowers = distributeWithImbalance(powerW, phases, 0.03);
                double[] phaseEnergies = distributeWithImbalance(energyWh, phases, 0.03);

                // En triphasé, chaque phase porte le courant nominal (PAS divisé par le nombre de phases!)
                // Le courant par phase EST le courant nominal avec un léger déséquilibre
                // session.getCurrentA() retourne déjà le courant par phase (calculé par SessionService)
                double[] phaseCurrents = applyImbalanceToEachPhase(currentA, phases, 0.03);

                // Puissance active totale
                sampledValues.add(createSampledValue(
                    String.valueOf((int) powerW),
                    "Power.Active.Import",
                    "W",
                    "Outlet",
                    null
                ));

                // Énergie et valeurs par phase
                String[] phaseNames = {"L1", "L2", "L3"};
                for (int i = 0; i < phases && i < 3; i++) {
                    String phase = phaseNames[i];

                    // Énergie par phase
                    sampledValues.add(createSampledValue(
                        String.valueOf((long) phaseEnergies[i]),
                        "Energy.Active.Import.Register",
                        "Wh",
                        "Outlet",
                        phase
                    ));

                    // Puissance par phase
                    sampledValues.add(createSampledValue(
                        String.valueOf((int) phasePowers[i]),
                        "Power.Active.Import",
                        "W",
                        "Outlet",
                        phase
                    ));

                    // Courant par phase
                    sampledValues.add(createSampledValue(
                        String.format("%.1f", phaseCurrents[i]),
                        "Current.Import",
                        "A",
                        "Outlet",
                        phase
                    ));

                    // Tension par phase (ligne-neutre)
                    // Ajouter une légère variation pour réalisme
                    double phaseVoltageVar = phaseVoltage * (1 + (i - 1) * 0.01);
                    sampledValues.add(createSampledValue(
                        String.format("%.1f", phaseVoltageVar),
                        "Voltage",
                        "V",
                        "Outlet",
                        phase
                    ));
                }

                // Tension ligne-ligne (entre phases) pour triphasé
                if (phases == 3) {
                    String[] lineNames = {"L1-L2", "L2-L3", "L3-L1"};
                    for (int i = 0; i < 3; i++) {
                        // Utiliser lineToLineVoltage calculée (√3 × tension phase-neutre ≈ 400V)
                        sampledValues.add(createSampledValue(
                            String.format("%.1f", lineToLineVoltage * (1 + (i - 1) * 0.005)),
                            "Voltage",
                            "V",
                            "Outlet",
                            lineNames[i]
                        ));
                    }
                }

                // Courant neutre (somme vectorielle, proche de 0 si équilibré)
                double neutralCurrent = Math.abs(phaseCurrents[0] - phaseCurrents[1]);
                if (phases == 3) {
                    neutralCurrent = Math.sqrt(
                        Math.pow(phaseCurrents[0] - phaseCurrents[1], 2) +
                        Math.pow(phaseCurrents[1] - phaseCurrents[2], 2) +
                        Math.pow(phaseCurrents[2] - phaseCurrents[0], 2)
                    ) / Math.sqrt(3);
                }
                sampledValues.add(createSampledValue(
                    String.format("%.2f", neutralCurrent),
                    "Current.Import",
                    "A",
                    "Outlet",
                    "N"
                ));

            } else {
                // Monophasé ou DC - comportement classique
                sampledValues.add(createSampledValue(
                    String.valueOf((int) powerW),
                    "Power.Active.Import",
                    "W",
                    "Outlet",
                    null
                ));

                if (currentA > 0) {
                    sampledValues.add(createSampledValue(
                        String.format("%.1f", currentA),
                        "Current.Import",
                        "A",
                        "Outlet",
                        null
                    ));
                }

                if (voltageV > 0) {
                    sampledValues.add(createSampledValue(
                        String.format("%.1f", voltageV),
                        "Voltage",
                        "V",
                        "Outlet",
                        null
                    ));
                }
            }

            // SoC (toujours inclus si disponible)
            if (session.getSoc() > 0) {
                sampledValues.add(createSampledValue(
                    String.valueOf((int) session.getSoc()),
                    "SoC",
                    "Percent",
                    null,
                    null
                ));
            }

            // Température (optionnel)
            if (session.getTemperature() > 0 && session.getTemperature() != 25.0) {
                sampledValues.add(createSampledValue(
                    String.format("%.1f", session.getTemperature()),
                    "Temperature",
                    "Celsius",
                    "Body",
                    null
                ));
            }
        }

        return createPayload(
            "timestamp", formatTimestamp(),
            "sampledValue", sampledValues
        );
    }

    /**
     * Distribue une valeur totale sur plusieurs phases avec un léger déséquilibre.
     * La somme des valeurs de sortie égale la valeur totale d'entrée.
     * @param total valeur totale à distribuer
     * @param phases nombre de phases (2 ou 3)
     * @param imbalance facteur de déséquilibre (ex: 0.03 = 3%)
     * @return tableau des valeurs par phase
     */
    private double[] distributeWithImbalance(double total, int phases, double imbalance) {
        double[] result = new double[phases];
        double base = total / phases;

        // Créer un léger déséquilibre réaliste
        for (int i = 0; i < phases; i++) {
            // Phase 0 (L1) légèrement plus haute, Phase 1 (L2) nominale, Phase 2 (L3) légèrement plus basse
            double factor = 1.0 + imbalance * (1 - i);
            result[i] = base * factor;
        }

        // Normaliser pour que la somme soit exacte
        double sum = 0;
        for (double v : result) sum += v;
        double correction = total / sum;
        for (int i = 0; i < phases; i++) {
            result[i] *= correction;
        }

        return result;
    }

    /**
     * Applique un léger déséquilibre à chaque phase SANS diviser la valeur.
     * Utilisé pour le courant en triphasé où chaque phase porte le courant nominal.
     * @param nominalValue valeur nominale par phase
     * @param phases nombre de phases (2 ou 3)
     * @param imbalance facteur de déséquilibre (ex: 0.03 = 3%)
     * @return tableau des valeurs par phase (chaque valeur proche de nominalValue)
     */
    private double[] applyImbalanceToEachPhase(double nominalValue, int phases, double imbalance) {
        double[] result = new double[phases];

        // Créer un léger déséquilibre réaliste autour de la valeur nominale
        for (int i = 0; i < phases; i++) {
            // Phase 0 (L1) légèrement plus haute, Phase 1 (L2) nominale, Phase 2 (L3) légèrement plus basse
            double factor = 1.0 + imbalance * (1 - i);
            result[i] = nominalValue * factor;
        }

        return result;
    }

    /**
     * Crée une valeur échantillonnée avec support optionnel de la phase.
     */
    private Map<String, Object> createSampledValue(String value, String measurand, String unit, String location, String phase) {
        Map<String, Object> sv = createPayload(
            "value", value,
            "measurand", measurand,
            "unit", unit
        );

        if (location != null) {
            sv.put("location", location);
        }

        if (phase != null) {
            sv.put("phase", phase);
        }

        return sv;
    }

    @Override
    public CompletableFuture<Map<String, Object>> handleResponse(String sessionId, Map<String, Object> response) {
        // MeterValues.conf est vide selon OCPP 1.6
        log.debug("[{}] MeterValues acknowledged", sessionId);
        return CompletableFuture.completedFuture(response);
    }
}
