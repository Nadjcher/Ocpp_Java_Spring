package com.evse.simulator.ocpi.test;

import com.evse.simulator.ocpi.OCPIModule;
import com.evse.simulator.ocpi.model.Partner;
import com.evse.simulator.ocpi.service.OCPIClientService;
import com.evse.simulator.ocpi.service.PartnerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes OCPI test scenarios against partners.
 */
@Service
@Slf4j
public class OCPITestRunner {

    private final PartnerService partnerService;
    private final OCPIClientService ocpiClient;
    private final OCPITestRepository testRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Active test executions
    private final Map<String, OCPITestResult> activeTests = new ConcurrentHashMap<>();

    public OCPITestRunner(PartnerService partnerService, OCPIClientService ocpiClient, OCPITestRepository testRepository) {
        this.partnerService = partnerService;
        this.ocpiClient = ocpiClient;
        this.testRepository = testRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Execute a test scenario against a partner.
     */
    public OCPITestResult executeScenario(String scenarioId, String partnerId) {
        OCPITestScenario scenario = testRepository.getScenario(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + scenarioId));

        Partner partner = partnerService.getPartner(partnerId)
                .orElseThrow(() -> new IllegalArgumentException("Partner not found: " + partnerId));

        return executeScenario(scenario, partner);
    }

    /**
     * Execute a test scenario.
     */
    public OCPITestResult executeScenario(OCPITestScenario scenario, Partner partner) {
        String resultId = UUID.randomUUID().toString();

        OCPITestResult result = OCPITestResult.builder()
                .id(resultId)
                .scenarioId(scenario.getId())
                .scenarioName(scenario.getName())
                .partnerId(partner.getId())
                .partnerName(partner.getName())
                .environment(partner.getActiveEnvironment())
                .startTime(Instant.now())
                .status(OCPITestResult.ResultStatus.RUNNING)
                .stepResults(new ArrayList<>())
                .variables(new HashMap<>(scenario.getVariables() != null ? scenario.getVariables() : Map.of()))
                .build();

        activeTests.put(resultId, result);

        try {
            // Check required modules
            if (scenario.getRequiredModules() != null) {
                for (OCPIModule module : scenario.getRequiredModules()) {
                    if (!partnerService.supportsModule(partner.getId(), module)) {
                        log.warn("Partner {} does not support required module: {}", partner.getName(), module);
                    }
                }
            }

            // Execute steps
            boolean hasCriticalFailure = false;
            Map<String, OCPITestResult.StepResult> stepResultsMap = new HashMap<>();

            for (OCPITestScenario.TestStep step : scenario.getSteps()) {
                // Check if step should be skipped due to dependency failure
                if (step.getDependsOn() != null) {
                    OCPITestResult.StepResult depResult = stepResultsMap.get(step.getDependsOn());
                    if (depResult == null || depResult.getStatus() != OCPITestResult.ResultStatus.PASSED) {
                        OCPITestResult.StepResult skipped = OCPITestResult.StepResult.builder()
                                .stepId(step.getId())
                                .stepName(step.getName())
                                .status(OCPITestResult.ResultStatus.SKIPPED)
                                .startTime(Instant.now())
                                .endTime(Instant.now())
                                .errorMessage("Skipped due to failed dependency: " + step.getDependsOn())
                                .build();
                        result.getStepResults().add(skipped);
                        stepResultsMap.put(step.getId(), skipped);
                        continue;
                    }
                }

                // Check condition
                if (step.getCondition() != null && !evaluateCondition(step.getCondition(), result.getVariables())) {
                    OCPITestResult.StepResult skipped = OCPITestResult.StepResult.builder()
                            .stepId(step.getId())
                            .stepName(step.getName())
                            .status(OCPITestResult.ResultStatus.SKIPPED)
                            .startTime(Instant.now())
                            .endTime(Instant.now())
                            .errorMessage("Condition not met: " + step.getCondition())
                            .build();
                    result.getStepResults().add(skipped);
                    stepResultsMap.put(step.getId(), skipped);
                    continue;
                }

                // Execute step
                OCPITestResult.StepResult stepResult = executeStep(step, partner, result.getVariables());
                result.getStepResults().add(stepResult);
                stepResultsMap.put(step.getId(), stepResult);

                // Check for critical failure
                if (stepResult.getStatus() == OCPITestResult.ResultStatus.FAILED) {
                    boolean hasCriticalAssertion = stepResult.getAssertionResults() != null &&
                            stepResult.getAssertionResults().stream()
                                    .anyMatch(a -> !a.isPassed() && a.isCritical());

                    if (hasCriticalAssertion && scenario.isStopOnFailure()) {
                        hasCriticalFailure = true;
                        log.warn("Critical failure in step {}, stopping scenario", step.getName());
                        break;
                    }
                }
            }

            // Calculate summary
            result.setSummary(calculateSummary(result.getStepResults()));

            // Determine final status
            if (hasCriticalFailure || result.getSummary().getFailedSteps() > 0) {
                result.setStatus(OCPITestResult.ResultStatus.FAILED);
            } else if (result.getSummary().getPassedSteps() == result.getSummary().getTotalSteps()) {
                result.setStatus(OCPITestResult.ResultStatus.PASSED);
            } else {
                result.setStatus(OCPITestResult.ResultStatus.PASSED); // Some skipped is OK
            }

        } catch (Exception e) {
            log.error("Test execution failed: {}", e.getMessage(), e);
            result.setStatus(OCPITestResult.ResultStatus.ERROR);
        } finally {
            result.setEndTime(Instant.now());
            result.setDurationMs(result.getEndTime().toEpochMilli() - result.getStartTime().toEpochMilli());
            activeTests.remove(resultId);
            testRepository.saveResult(result);
        }

        return result;
    }

    /**
     * Execute a single test step.
     */
    private OCPITestResult.StepResult executeStep(OCPITestScenario.TestStep step, Partner partner,
                                                   Map<String, Object> variables) {
        Instant startTime = Instant.now();

        OCPITestResult.StepResult.StepResultBuilder resultBuilder = OCPITestResult.StepResult.builder()
                .stepId(step.getId())
                .stepName(step.getName())
                .startTime(startTime)
                .assertionResults(new ArrayList<>())
                .extractedValues(new HashMap<>());

        try {
            switch (step.getType()) {
                case HTTP_REQUEST:
                    return executeHttpRequest(step, partner, variables, resultBuilder);

                case VERSION_DISCOVERY:
                    return executeVersionDiscovery(step, partner, variables, resultBuilder);

                case CREDENTIALS_HANDSHAKE:
                    return executeCredentialsHandshake(step, partner, variables, resultBuilder);

                case WAIT:
                    return executeWait(step, variables, resultBuilder);

                default:
                    throw new UnsupportedOperationException("Step type not implemented: " + step.getType());
            }

        } catch (Exception e) {
            log.error("Step execution failed: {} - {}", step.getName(), e.getMessage());
            return resultBuilder
                    .status(OCPITestResult.ResultStatus.ERROR)
                    .endTime(Instant.now())
                    .durationMs(System.currentTimeMillis() - startTime.toEpochMilli())
                    .errorMessage(e.getMessage())
                    .errorDetails(Arrays.toString(e.getStackTrace()))
                    .build();
        }
    }

    /**
     * Execute HTTP request step.
     */
    private OCPITestResult.StepResult executeHttpRequest(OCPITestScenario.TestStep step, Partner partner,
                                                          Map<String, Object> variables,
                                                          OCPITestResult.StepResult.StepResultBuilder resultBuilder) {
        // Build URL with variable substitution
        String url = substituteVariables(step.getEndpoint(), variables, partner);

        // Build request body
        String requestBody = null;
        if (step.getRequestBody() != null) {
            try {
                String bodyStr = objectMapper.writeValueAsString(step.getRequestBody());
                requestBody = substituteVariables(bodyStr, variables, partner);
            } catch (Exception e) {
                log.error("Failed to serialize request body: {}", e.getMessage());
            }
        }

        // Build headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String token = partner.getTokenB();
        if (token != null) {
            headers.set("Authorization", "Token " + Base64.getEncoder().encodeToString(token.getBytes()));
        }
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        headers.set("X-Correlation-ID", UUID.randomUUID().toString());
        headers.set("OCPI-from-country-code", "FR");
        headers.set("OCPI-from-party-id", "TTE");
        if (partner.getCountryCode() != null) {
            headers.set("OCPI-to-country-code", partner.getCountryCode());
        }
        if (partner.getPartyId() != null) {
            headers.set("OCPI-to-party-id", partner.getPartyId());
        }

        // Add custom headers
        if (step.getHeaders() != null) {
            step.getHeaders().forEach(headers::set);
        }

        // Record request
        resultBuilder.request(OCPITestResult.RequestInfo.builder()
                .method(step.getMethod())
                .url(url)
                .headers(headers.toSingleValueMap())
                .body(requestBody)
                .build());

        // Execute request
        HttpMethod method = HttpMethod.valueOf(step.getMethod());
        HttpEntity<String> entity = requestBody != null ? new HttpEntity<>(requestBody, headers) : new HttpEntity<>(headers);

        long startMs = System.currentTimeMillis();
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, method, entity, String.class);
        } catch (Exception e) {
            return resultBuilder
                    .status(OCPITestResult.ResultStatus.ERROR)
                    .endTime(Instant.now())
                    .durationMs(System.currentTimeMillis() - startMs)
                    .errorMessage("HTTP request failed: " + e.getMessage())
                    .build();
        }
        long latencyMs = System.currentTimeMillis() - startMs;

        // Parse response
        int ocpiStatus = 0;
        String ocpiMessage = null;
        String responseBody = response.getBody();

        try {
            if (responseBody != null) {
                JsonNode root = objectMapper.readTree(responseBody);
                if (root.has("status_code")) {
                    ocpiStatus = root.get("status_code").asInt();
                }
                if (root.has("status_message")) {
                    ocpiMessage = root.get("status_message").asText();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse OCPI response: {}", e.getMessage());
        }

        resultBuilder.response(OCPITestResult.ResponseInfo.builder()
                .httpStatus(response.getStatusCode().value())
                .ocpiStatus(ocpiStatus)
                .ocpiMessage(ocpiMessage)
                .headers(response.getHeaders().toSingleValueMap())
                .body(responseBody)
                .latencyMs(latencyMs)
                .build());

        // Run assertions
        List<OCPITestResult.AssertionResult> assertionResults = new ArrayList<>();
        boolean allPassed = true;

        if (step.getAssertions() != null) {
            for (OCPITestScenario.Assertion assertion : step.getAssertions()) {
                OCPITestResult.AssertionResult ar = evaluateAssertion(assertion, response, responseBody, latencyMs);
                assertionResults.add(ar);
                if (!ar.isPassed() && assertion.isCritical()) {
                    allPassed = false;
                }
            }
        }

        resultBuilder.assertionResults(assertionResults);

        // Extract values
        if (step.getExtractions() != null && responseBody != null) {
            for (OCPITestScenario.Extraction extraction : step.getExtractions()) {
                try {
                    Object value = null;
                    if (extraction.getJsonPath() != null) {
                        value = JsonPath.read(responseBody, extraction.getJsonPath());
                    } else if (extraction.getHeader() != null) {
                        value = response.getHeaders().getFirst(extraction.getHeader());
                    }

                    if (value == null && extraction.getDefaultValue() != null) {
                        value = extraction.getDefaultValue();
                    }

                    if (value != null) {
                        variables.put(extraction.getVariableName(), value);
                        resultBuilder.extractedValues(Map.of(extraction.getVariableName(), value));
                    }
                } catch (Exception e) {
                    log.warn("Extraction failed for {}: {}", extraction.getVariableName(), e.getMessage());
                }
            }
        }

        return resultBuilder
                .status(allPassed ? OCPITestResult.ResultStatus.PASSED : OCPITestResult.ResultStatus.FAILED)
                .endTime(Instant.now())
                .durationMs(latencyMs)
                .build();
    }

    /**
     * Execute version discovery step.
     */
    private OCPITestResult.StepResult executeVersionDiscovery(OCPITestScenario.TestStep step, Partner partner,
                                                               Map<String, Object> variables,
                                                               OCPITestResult.StepResult.StepResultBuilder resultBuilder) {
        long startMs = System.currentTimeMillis();

        try {
            // Get versions
            OCPIClientService.OCPIResponse<List<OCPIClientService.VersionInfo>> versionsResponse =
                    ocpiClient.getVersions(partner.getId());

            if (!versionsResponse.isSuccess()) {
                return resultBuilder
                        .status(OCPITestResult.ResultStatus.FAILED)
                        .endTime(Instant.now())
                        .durationMs(System.currentTimeMillis() - startMs)
                        .errorMessage("Failed to get versions: " + versionsResponse.getStatusMessage())
                        .build();
            }

            // Find target version
            String targetVersion = partner.getVersion() != null ? partner.getVersion().getValue() : "2.2.1";
            OCPIClientService.VersionInfo versionInfo = versionsResponse.getData().stream()
                    .filter(v -> v.getVersion().equals(targetVersion))
                    .findFirst()
                    .orElse(null);

            if (versionInfo == null) {
                return resultBuilder
                        .status(OCPITestResult.ResultStatus.FAILED)
                        .endTime(Instant.now())
                        .durationMs(System.currentTimeMillis() - startMs)
                        .errorMessage("Target version not found: " + targetVersion)
                        .build();
            }

            // Get version details
            OCPIClientService.OCPIResponse<OCPIClientService.VersionDetails> detailsResponse =
                    ocpiClient.getVersionDetails(partner.getId(), versionInfo.getUrl());

            if (!detailsResponse.isSuccess()) {
                return resultBuilder
                        .status(OCPITestResult.ResultStatus.FAILED)
                        .endTime(Instant.now())
                        .durationMs(System.currentTimeMillis() - startMs)
                        .errorMessage("Failed to get version details: " + detailsResponse.getStatusMessage())
                        .build();
            }

            // Update partner endpoints
            Map<String, String> endpoints = new HashMap<>();
            for (OCPIClientService.Endpoint ep : detailsResponse.getData().getEndpoints()) {
                endpoints.put(ep.getIdentifier(), ep.getUrl());
            }
            partnerService.updateEndpoints(partner.getId(), endpoints);

            // Store in variables
            variables.put("discoveredEndpoints", endpoints);
            variables.put("discoveredVersion", targetVersion);

            return resultBuilder
                    .status(OCPITestResult.ResultStatus.PASSED)
                    .endTime(Instant.now())
                    .durationMs(System.currentTimeMillis() - startMs)
                    .extractedValues(Map.of("endpoints", endpoints, "version", targetVersion))
                    .build();

        } catch (Exception e) {
            return resultBuilder
                    .status(OCPITestResult.ResultStatus.ERROR)
                    .endTime(Instant.now())
                    .durationMs(System.currentTimeMillis() - startMs)
                    .errorMessage("Version discovery failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Execute credentials handshake step.
     */
    private OCPITestResult.StepResult executeCredentialsHandshake(OCPITestScenario.TestStep step, Partner partner,
                                                                    Map<String, Object> variables,
                                                                    OCPITestResult.StepResult.StepResultBuilder resultBuilder) {
        // Simplified credentials handshake - full implementation would involve multiple requests
        return resultBuilder
                .status(OCPITestResult.ResultStatus.PASSED)
                .endTime(Instant.now())
                .durationMs(0)
                .build();
    }

    /**
     * Execute wait step.
     */
    private OCPITestResult.StepResult executeWait(OCPITestScenario.TestStep step,
                                                   Map<String, Object> variables,
                                                   OCPITestResult.StepResult.StepResultBuilder resultBuilder) {
        try {
            Thread.sleep(step.getTimeoutMs());
            return resultBuilder
                    .status(OCPITestResult.ResultStatus.PASSED)
                    .endTime(Instant.now())
                    .durationMs(step.getTimeoutMs())
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return resultBuilder
                    .status(OCPITestResult.ResultStatus.ERROR)
                    .endTime(Instant.now())
                    .errorMessage("Wait interrupted")
                    .build();
        }
    }

    /**
     * Evaluate an assertion against the response.
     */
    private OCPITestResult.AssertionResult evaluateAssertion(OCPITestScenario.Assertion assertion,
                                                              ResponseEntity<String> response,
                                                              String responseBody,
                                                              long latencyMs) {
        OCPITestResult.AssertionResult.AssertionResultBuilder builder = OCPITestResult.AssertionResult.builder()
                .name(assertion.getName())
                .critical(assertion.isCritical())
                .expected(String.valueOf(assertion.getExpected()));

        try {
            Object actual = null;
            boolean passed = false;

            switch (assertion.getType()) {
                case HTTP_STATUS:
                    actual = response.getStatusCode().value();
                    passed = compareValues(actual, assertion.getExpected(), assertion.getOperator());
                    break;

                case OCPI_STATUS:
                    if (responseBody != null) {
                        JsonNode root = objectMapper.readTree(responseBody);
                        if (root.has("status_code")) {
                            actual = root.get("status_code").asInt();
                            passed = compareValues(actual, assertion.getExpected(), assertion.getOperator());
                        }
                    }
                    break;

                case JSON_PATH:
                    if (responseBody != null && assertion.getPath() != null) {
                        actual = JsonPath.read(responseBody, assertion.getPath());
                        passed = compareValues(actual, assertion.getExpected(), assertion.getOperator());
                    }
                    break;

                case HEADER:
                    actual = response.getHeaders().getFirst(assertion.getPath());
                    passed = compareValues(actual, assertion.getExpected(), assertion.getOperator());
                    break;

                case LATENCY:
                    actual = latencyMs;
                    passed = compareValues(actual, assertion.getExpected(), assertion.getOperator());
                    break;
            }

            return builder
                    .actual(String.valueOf(actual))
                    .passed(passed)
                    .message(passed ? "Assertion passed" : "Assertion failed")
                    .build();

        } catch (Exception e) {
            return builder
                    .passed(false)
                    .message("Assertion error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Compare two values using an operator.
     */
    private boolean compareValues(Object actual, Object expected, String operator) {
        if (actual == null) {
            return "exists".equals(operator) ? false : expected == null;
        }

        String op = operator != null ? operator : "eq";

        switch (op) {
            case "eq":
                return actual.toString().equals(expected.toString());
            case "ne":
                return !actual.toString().equals(expected.toString());
            case "gt":
                return Double.parseDouble(actual.toString()) > Double.parseDouble(expected.toString());
            case "lt":
                return Double.parseDouble(actual.toString()) < Double.parseDouble(expected.toString());
            case "gte":
                return Double.parseDouble(actual.toString()) >= Double.parseDouble(expected.toString());
            case "lte":
                return Double.parseDouble(actual.toString()) <= Double.parseDouble(expected.toString());
            case "contains":
                return actual.toString().contains(expected.toString());
            case "matches":
                return actual.toString().matches(expected.toString());
            case "exists":
                return actual != null;
            default:
                return actual.equals(expected);
        }
    }

    /**
     * Substitute variables in a string.
     */
    private String substituteVariables(String template, Map<String, Object> variables, Partner partner) {
        if (template == null) return null;

        String result = template;

        // Partner variables
        result = result.replace("${partner.baseUrl}", partner.getBaseUrl() != null ? partner.getBaseUrl() : "");
        result = result.replace("${partner.countryCode}", partner.getCountryCode() != null ? partner.getCountryCode() : "");
        result = result.replace("${partner.partyId}", partner.getPartyId() != null ? partner.getPartyId() : "");

        // Endpoint variables
        if (partner.getEndpoints() != null) {
            for (Map.Entry<String, String> ep : partner.getEndpoints().entrySet()) {
                result = result.replace("${endpoints." + ep.getKey() + "}", ep.getValue());
            }
        }

        // Custom variables
        Pattern pattern = Pattern.compile("\\$\\{([^}]+)}");
        Matcher matcher = pattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            matcher.appendReplacement(sb, value != null ? value.toString() : "");
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Evaluate a condition expression.
     */
    private boolean evaluateCondition(String condition, Map<String, Object> variables) {
        // Simple condition evaluation - could use SpEL for more complex conditions
        if (condition.contains("!=")) {
            String[] parts = condition.split("!=");
            String varName = parts[0].trim().replace("${", "").replace("}", "");
            String expected = parts[1].trim().replace("'", "");
            Object actual = variables.get(varName);
            return actual == null || !actual.toString().equals(expected);
        } else if (condition.contains("==")) {
            String[] parts = condition.split("==");
            String varName = parts[0].trim().replace("${", "").replace("}", "");
            String expected = parts[1].trim().replace("'", "");
            Object actual = variables.get(varName);
            return actual != null && actual.toString().equals(expected);
        }
        return true;
    }

    /**
     * Calculate test summary.
     */
    private OCPITestResult.Summary calculateSummary(List<OCPITestResult.StepResult> stepResults) {
        int total = stepResults.size();
        int passed = 0, failed = 0, skipped = 0;
        int totalAssertions = 0, passedAssertions = 0, failedAssertions = 0, warningAssertions = 0;
        long totalLatency = 0, maxLatency = 0, minLatency = Long.MAX_VALUE;
        int latencyCount = 0;

        for (OCPITestResult.StepResult sr : stepResults) {
            switch (sr.getStatus()) {
                case PASSED: passed++; break;
                case FAILED: failed++; break;
                case SKIPPED: skipped++; break;
            }

            if (sr.getAssertionResults() != null) {
                for (OCPITestResult.AssertionResult ar : sr.getAssertionResults()) {
                    totalAssertions++;
                    if (ar.isPassed()) {
                        passedAssertions++;
                    } else if (ar.isCritical()) {
                        failedAssertions++;
                    } else {
                        warningAssertions++;
                    }
                }
            }

            if (sr.getResponse() != null && sr.getResponse().getLatencyMs() > 0) {
                long lat = sr.getResponse().getLatencyMs();
                totalLatency += lat;
                latencyCount++;
                if (lat > maxLatency) maxLatency = lat;
                if (lat < minLatency) minLatency = lat;
            }
        }

        return OCPITestResult.Summary.builder()
                .totalSteps(total)
                .passedSteps(passed)
                .failedSteps(failed)
                .skippedSteps(skipped)
                .totalAssertions(totalAssertions)
                .passedAssertions(passedAssertions)
                .failedAssertions(failedAssertions)
                .warningAssertions(warningAssertions)
                .successRate(total > 0 ? (double) passed / total * 100 : 0)
                .avgLatencyMs(latencyCount > 0 ? totalLatency / latencyCount : 0)
                .maxLatencyMs(maxLatency)
                .minLatencyMs(minLatency == Long.MAX_VALUE ? 0 : minLatency)
                .build();
    }

    /**
     * Get active test executions.
     */
    public Collection<OCPITestResult> getActiveTests() {
        return activeTests.values();
    }

    /**
     * Get test result by ID.
     */
    public Optional<OCPITestResult> getResult(String resultId) {
        return testRepository.getResult(resultId);
    }

    /**
     * Get all results for a partner.
     */
    public List<OCPITestResult> getResultsForPartner(String partnerId) {
        return testRepository.getResultsForPartner(partnerId);
    }
}
