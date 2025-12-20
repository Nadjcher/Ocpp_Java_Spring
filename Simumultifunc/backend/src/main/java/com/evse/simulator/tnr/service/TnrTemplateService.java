package com.evse.simulator.tnr.service;

import com.evse.simulator.tnr.model.*;
import com.evse.simulator.tnr.model.TnrStep.StepType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de gestion des templates TNR.
 * <p>
 * Fournit des scénarios prédéfinis (templates) qui peuvent être
 * instanciés avec des paramètres personnalisés.
 * </p>
 */
@Service
@Slf4j
public class TnrTemplateService {

    @Getter
    private final Map<String, TnrScenario> templates = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("Initializing TNR templates...");
        registerTemplate(createSimpleChargeTemplate());
        registerTemplate(createSmartChargingTemplate());
        registerTemplate(createMultiSessionTemplate());
        registerTemplate(createErrorRecoveryTemplate());
        registerTemplate(createDCFastChargeTemplate());
        log.info("Loaded {} TNR templates", templates.size());
    }

    private void registerTemplate(TnrScenario template) {
        templates.put(template.getId(), template);
    }

    /**
     * Récupère tous les templates disponibles.
     */
    public List<TnrScenario> getAllTemplates() {
        return new ArrayList<>(templates.values());
    }

    /**
     * Récupère un template par ID.
     */
    public Optional<TnrScenario> getTemplate(String templateId) {
        return Optional.ofNullable(templates.get(templateId));
    }

    /**
     * Instancie un template avec les paramètres fournis.
     */
    public TnrScenario instantiate(String templateId, TemplateParams params) {
        TnrScenario template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }

        log.info("Instantiating template {} with params: {}", templateId, params);

        // Cloner le template
        TnrScenario scenario = cloneScenario(template);

        // Générer un nouvel ID
        scenario.setId(UUID.randomUUID().toString());
        scenario.setTemplate(false);
        scenario.setTemplateId(templateId);

        // Appliquer le nom personnalisé
        if (params.getName() != null && !params.getName().isBlank()) {
            scenario.setName(params.getName());
        } else {
            scenario.setName(template.getName() + " - " + Instant.now().toString().substring(0, 19));
        }

        // Appliquer les tags
        if (params.getTags() != null && !params.getTags().isEmpty()) {
            List<String> allTags = new ArrayList<>(template.getTags());
            allTags.addAll(params.getTags());
            scenario.setTags(allTags);
        }

        // Substituer les variables dans les steps
        Map<String, String> variables = buildVariables(params);
        substituteVariables(scenario, variables);

        // Mettre à jour les métadonnées
        scenario.setMetadata(TnrMetadata.builder()
                .version("1.0")
                .createdAt(Instant.now())
                .createdBy(params.getAuthor() != null ? params.getAuthor() : "user")
                .runCount(0)
                .passCount(0)
                .failCount(0)
                .build());

        // Appliquer la config personnalisée
        if (params.getConfig() != null) {
            mergeConfig(scenario.getConfig(), params.getConfig());
        }

        scenario.setCreatedAt(Instant.now());
        scenario.setUpdatedAt(Instant.now());

        return scenario;
    }

    private Map<String, String> buildVariables(TemplateParams params) {
        Map<String, String> vars = new HashMap<>();
        vars.put("{{CP_ID}}", params.getCpId() != null ? params.getCpId() : "CP001");
        vars.put("{{CONNECTOR_ID}}", String.valueOf(params.getConnectorId() != null ? params.getConnectorId() : 1));
        vars.put("{{VEHICLE_ID}}", params.getVehicleId() != null ? params.getVehicleId() : "TESLA_MODEL3_LR");
        vars.put("{{ID_TAG}}", params.getIdTag() != null ? params.getIdTag() : "DEFAULT_TAG");
        vars.put("{{INITIAL_SOC}}", String.valueOf(params.getInitialSoc() != null ? params.getInitialSoc() : 20));
        vars.put("{{TARGET_SOC}}", String.valueOf(params.getTargetSoc() != null ? params.getTargetSoc() : 80));
        vars.put("{{CHARGER_TYPE}}", params.getChargerType() != null ? params.getChargerType() : "AC_TRI");
        return vars;
    }

    private void substituteVariables(TnrScenario scenario, Map<String, String> variables) {
        // Substituer dans la description
        if (scenario.getDescription() != null) {
            scenario.setDescription(substitute(scenario.getDescription(), variables));
        }

        // Substituer dans les steps
        for (TnrStep step : scenario.getSteps()) {
            if (step.getText() != null) {
                step.setText(substitute(step.getText(), variables));
            }
            if (step.getParams() != null) {
                Map<String, Object> newParams = new HashMap<>();
                for (Map.Entry<String, Object> entry : step.getParams().entrySet()) {
                    if (entry.getValue() instanceof String) {
                        newParams.put(entry.getKey(), substitute((String) entry.getValue(), variables));
                    } else {
                        newParams.put(entry.getKey(), entry.getValue());
                    }
                }
                step.setParams(newParams);
            }
        }

        // Substituer dans les parameters du scénario
        if (scenario.getParameters() != null) {
            Map<String, Object> newParams = new HashMap<>();
            for (Map.Entry<String, Object> entry : scenario.getParameters().entrySet()) {
                if (entry.getValue() instanceof String) {
                    newParams.put(entry.getKey(), substitute((String) entry.getValue(), variables));
                } else {
                    newParams.put(entry.getKey(), entry.getValue());
                }
            }
            scenario.setParameters(newParams);
        }
    }

    private String substitute(String text, Map<String, String> variables) {
        String result = text;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private void mergeConfig(TnrConfig target, TnrConfig source) {
        if (source.getTimeScale() > 0) {
            target.setTimeScale(source.getTimeScale());
        }
        if (source.getMaxDurationSec() > 0) {
            target.setMaxDurationSec(source.getMaxDurationSec());
        }
        if (source.getTolerances() != null) {
            target.setTolerances(source.getTolerances());
        }
        target.setContinueOnError(source.isContinueOnError());
        target.setStopOnError(source.isStopOnError());
    }

    private TnrScenario cloneScenario(TnrScenario template) {
        return TnrScenario.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .category(template.getCategory())
                .tags(new ArrayList<>(template.getTags()))
                .config(cloneConfig(template.getConfig()))
                .expectedResults(template.getExpectedResults())
                .gherkinContent(template.getGherkinContent())
                .steps(cloneSteps(template.getSteps()))
                .backgroundSteps(cloneSteps(template.getBackgroundSteps()))
                .parameters(new HashMap<>(template.getParameters()))
                .dependencies(new ArrayList<>(template.getDependencies()))
                .priority(template.getPriority())
                .timeoutSeconds(template.getTimeoutSeconds())
                .maxRetries(template.getMaxRetries())
                .critical(template.isCritical())
                .enabled(template.isEnabled())
                .template(template.isTemplate())
                .build();
    }

    private TnrConfig cloneConfig(TnrConfig config) {
        if (config == null) return TnrConfig.defaults();
        return TnrConfig.builder()
                .mode(config.getMode())
                .stopOnError(config.isStopOnError())
                .continueOnError(config.isContinueOnError())
                .captureResponses(config.isCaptureResponses())
                .timeoutMs(config.getTimeoutMs())
                .retryCount(config.getRetryCount())
                .delayBetweenStepsMs(config.getDelayBetweenStepsMs())
                .timeScale(config.getTimeScale())
                .maxDurationSec(config.getMaxDurationSec())
                .csmsSimulation(config.isCsmsSimulation())
                .tolerances(config.getTolerances())
                .ignoredFields(new ArrayList<>(config.getIgnoredFields()))
                .variables(new HashMap<>(config.getVariables()))
                .injectChargingProfiles(new ArrayList<>(config.getInjectChargingProfiles()))
                .build();
    }

    private List<TnrStep> cloneSteps(List<TnrStep> steps) {
        if (steps == null) return new ArrayList<>();
        List<TnrStep> cloned = new ArrayList<>();
        for (TnrStep step : steps) {
            cloned.add(TnrStep.builder()
                    .id(step.getId())
                    .order(step.getOrder())
                    .type(step.getType())
                    .keyword(step.getKeyword())
                    .text(step.getText())
                    .params(step.getParams() != null ? new HashMap<>(step.getParams()) : null)
                    .optional(step.isOptional())
                    .timeoutMs(step.getTimeoutMs())
                    .retryCount(step.getRetryCount())
                    .retryDelayMs(step.getRetryDelayMs())
                    .build());
        }
        return cloned;
    }

    // ═══════════════════════════════════════════════════════════════════
    // TEMPLATES PRÉDÉFINIS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Template: Charge simple AC
     */
    private TnrScenario createSimpleChargeTemplate() {
        return TnrScenario.builder()
                .id("TEMPLATE_SIMPLE_CHARGE")
                .name("Simple AC Charge")
                .description("Basic charging session from {{INITIAL_SOC}}% to {{TARGET_SOC}}% SOC")
                .tags(List.of("smoke", "ac", "basic", "template"))
                .category("smoke")
                .template(true)
                .config(TnrConfig.builder()
                        .timeScale(10.0)
                        .maxDurationSec(300)
                        .csmsSimulation(true)
                        .tolerances(TnrTolerances.defaults())
                        .continueOnError(false)
                        .build())
                .steps(List.of(
                        step(0, StepType.GIVEN, "a charger {{CP_ID}} with connector {{CONNECTOR_ID}}"),
                        step(1, StepType.AND, "vehicle {{VEHICLE_ID}} at {{INITIAL_SOC}}% SOC"),
                        step(2, StepType.WHEN, "the vehicle connects"),
                        step(3, StepType.AND, "the cable is plugged"),
                        step(4, StepType.AND, "authorization with tag {{ID_TAG}}"),
                        step(5, StepType.AND, "charging starts"),
                        step(6, StepType.THEN, "wait until SOC reaches {{TARGET_SOC}}%"),
                        step(7, StepType.AND, "stop charging"),
                        step(8, StepType.THEN, "verify SOC is at least {{TARGET_SOC}}%")
                ))
                .expectedResults(TnrExpectedResults.builder()
                        .allSessionsCompleted(true)
                        .ocppErrors(0)
                        .build())
                .metadata(TnrMetadata.builder()
                        .version("1.0")
                        .createdAt(Instant.now())
                        .createdBy("TnrTemplates")
                        .build())
                .build();
    }

    /**
     * Template: Smart Charging avec limite de puissance
     */
    private TnrScenario createSmartChargingTemplate() {
        return TnrScenario.builder()
                .id("TEMPLATE_SMART_CHARGING")
                .name("Smart Charging Profile")
                .description("Test charging with power limit profile (7kW then 11kW after 5 min)")
                .tags(List.of("smart-charging", "scp", "txprofile", "template"))
                .category("smart-charging")
                .template(true)
                .config(TnrConfig.builder()
                        .timeScale(5.0)
                        .maxDurationSec(600)
                        .csmsSimulation(true)
                        .tolerances(TnrTolerances.defaults())
                        .continueOnError(false)
                        .injectChargingProfiles(List.of(
                                Map.of(
                                        "chargingProfileId", 1,
                                        "stackLevel", 1,
                                        "chargingProfilePurpose", "TxProfile",
                                        "chargingProfileKind", "Relative",
                                        "chargingSchedule", Map.of(
                                                "chargingRateUnit", "W",
                                                "chargingSchedulePeriod", List.of(
                                                        Map.of("startPeriod", 0, "limit", 7000.0, "numberPhases", 3),
                                                        Map.of("startPeriod", 300, "limit", 11000.0, "numberPhases", 3)
                                                )
                                        )
                                )
                        ))
                        .build())
                .steps(List.of(
                        step(0, StepType.GIVEN, "a charger {{CP_ID}} with connector {{CONNECTOR_ID}}"),
                        step(1, StepType.AND, "vehicle {{VEHICLE_ID}} at 30% SOC"),
                        step(2, StepType.WHEN, "the vehicle connects and plugs"),
                        step(3, StepType.AND, "authorization with tag {{ID_TAG}}"),
                        step(4, StepType.AND, "charging starts"),
                        step(5, StepType.THEN, "verify power is at most 7.5 kW after 5 seconds"),
                        step(6, StepType.WHEN, "wait 5 minutes"),
                        step(7, StepType.THEN, "verify power is greater than 7 kW"),
                        step(8, StepType.AND, "stop charging after 1 minute")
                ))
                .expectedResults(TnrExpectedResults.builder()
                        .allSessionsCompleted(true)
                        .scpProfilesApplied(1)
                        .scpLimitRespected(true)
                        .ocppErrors(0)
                        .build())
                .metadata(TnrMetadata.builder()
                        .version("1.0")
                        .createdAt(Instant.now())
                        .createdBy("TnrTemplates")
                        .build())
                .build();
    }

    /**
     * Template: Multi-sessions simultanées
     */
    private TnrScenario createMultiSessionTemplate() {
        return TnrScenario.builder()
                .id("TEMPLATE_MULTI_SESSION")
                .name("Multi-Session Charging")
                .description("Two vehicles charging simultaneously on different connectors")
                .tags(List.of("multi-session", "parallel", "load", "template"))
                .category("multi-session")
                .template(true)
                .config(TnrConfig.builder()
                        .timeScale(10.0)
                        .maxDurationSec(600)
                        .csmsSimulation(true)
                        .tolerances(TnrTolerances.relaxed())
                        .continueOnError(true)
                        .build())
                .steps(List.of(
                        step(0, StepType.GIVEN, "a charger {{CP_ID}} with 2 connectors"),
                        step(1, StepType.AND, "Tesla Model 3 at 20% SOC on connector 1"),
                        step(2, StepType.AND, "Renault Zoe at 30% SOC on connector 2"),
                        step(3, StepType.WHEN, "both vehicles connect simultaneously"),
                        step(4, StepType.AND, "both authorize and start charging"),
                        step(5, StepType.THEN, "Tesla reaches 50% SOC"),
                        step(6, StepType.AND, "Zoe reaches 60% SOC"),
                        step(7, StepType.AND, "both sessions stop successfully")
                ))
                .expectedResults(TnrExpectedResults.builder()
                        .allSessionsCompleted(true)
                        .ocppErrors(0)
                        .build())
                .metadata(TnrMetadata.builder()
                        .version("1.0")
                        .createdAt(Instant.now())
                        .createdBy("TnrTemplates")
                        .build())
                .build();
    }

    /**
     * Template: Gestion des erreurs et recovery
     */
    private TnrScenario createErrorRecoveryTemplate() {
        return TnrScenario.builder()
                .id("TEMPLATE_ERROR_RECOVERY")
                .name("Error Recovery")
                .description("Test reconnection and recovery after connection loss")
                .tags(List.of("error", "recovery", "edge-case", "template"))
                .category("edge-case")
                .template(true)
                .config(TnrConfig.builder()
                        .timeScale(5.0)
                        .maxDurationSec(300)
                        .csmsSimulation(true)
                        .tolerances(TnrTolerances.defaults())
                        .continueOnError(true)
                        .build())
                .steps(List.of(
                        step(0, StepType.GIVEN, "a charger {{CP_ID}} with connector {{CONNECTOR_ID}}"),
                        step(1, StepType.AND, "vehicle {{VEHICLE_ID}} at 40% SOC"),
                        step(2, StepType.WHEN, "the vehicle connects and starts charging"),
                        step(3, StepType.AND, "charging runs for 10 seconds"),
                        step(4, StepType.WHEN, "connection is lost (disconnect)"),
                        step(5, StepType.AND, "wait 5 seconds"),
                        step(6, StepType.WHEN, "connection is restored (reconnect)"),
                        step(7, StepType.AND, "charging resumes"),
                        step(8, StepType.THEN, "wait until SOC reaches 60%"),
                        step(9, StepType.AND, "stop charging successfully")
                ))
                .expectedResults(TnrExpectedResults.builder()
                        .allSessionsCompleted(true)
                        .build())
                .metadata(TnrMetadata.builder()
                        .version("1.0")
                        .createdAt(Instant.now())
                        .createdBy("TnrTemplates")
                        .build())
                .build();
    }

    /**
     * Template: Charge rapide DC
     */
    private TnrScenario createDCFastChargeTemplate() {
        return TnrScenario.builder()
                .id("TEMPLATE_DC_FAST_CHARGE")
                .name("DC Fast Charging")
                .description("High-power DC charging session with power curve validation")
                .tags(List.of("dc", "fast-charge", "high-power", "template"))
                .category("regression")
                .template(true)
                .config(TnrConfig.builder()
                        .timeScale(5.0)
                        .maxDurationSec(400)
                        .csmsSimulation(true)
                        .tolerances(TnrTolerances.strict())
                        .continueOnError(false)
                        .build())
                .steps(List.of(
                        step(0, StepType.GIVEN, "a DC charger {{CP_ID}} with CCS connector"),
                        step(1, StepType.AND, "Tesla Model 3 at 10% SOC"),
                        step(2, StepType.WHEN, "the vehicle connects to DC charger"),
                        step(3, StepType.AND, "authorization with tag {{ID_TAG}}"),
                        step(4, StepType.AND, "DC charging starts"),
                        step(5, StepType.THEN, "verify initial power is above 100 kW"),
                        step(6, StepType.WHEN, "SOC reaches 50%"),
                        step(7, StepType.THEN, "verify power has tapered (below 100 kW)"),
                        step(8, StepType.WHEN, "SOC reaches 80%"),
                        step(9, StepType.AND, "stop charging"),
                        step(10, StepType.THEN, "verify total energy delivered is correct")
                ))
                .expectedResults(TnrExpectedResults.builder()
                        .allSessionsCompleted(true)
                        .ocppErrors(0)
                        .build())
                .metadata(TnrMetadata.builder()
                        .version("1.0")
                        .createdAt(Instant.now())
                        .createdBy("TnrTemplates")
                        .build())
                .build();
    }

    private TnrStep step(int order, StepType type, String text) {
        return TnrStep.builder()
                .id(UUID.randomUUID().toString())
                .order(order)
                .type(type)
                .keyword(type.name())
                .text(text)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // DTO POUR LES PARAMÈTRES DE TEMPLATE
    // ═══════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TemplateParams {
        private String name;
        private String cpId;
        private Integer connectorId;
        private String vehicleId;
        private String idTag;
        private Integer initialSoc;
        private Integer targetSoc;
        private String chargerType;
        private List<String> tags;
        private String author;
        private TnrConfig config;
    }
}
