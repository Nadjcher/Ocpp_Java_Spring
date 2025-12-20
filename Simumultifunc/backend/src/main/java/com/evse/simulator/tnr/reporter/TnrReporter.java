package com.evse.simulator.tnr.reporter;

import com.evse.simulator.tnr.model.TnrResult;
import com.evse.simulator.tnr.model.TnrSuiteResult;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface commune pour les reporters TNR.
 * <p>
 * Permet de générer des rapports d'exécution dans différents formats.
 * </p>
 *
 * @example
 * <pre>
 * TnrReporter reporter = new JsonReporter();
 * reporter.generateReport(suiteResult, Path.of("report.json"));
 *
 * // Ou pour un scénario unique
 * reporter.generateReport(scenarioResult, Path.of("scenario-report.json"));
 * </pre>
 */
public interface TnrReporter {

    /**
     * Format de sortie du reporter.
     */
    enum Format {
        JSON,
        HTML,
        JUNIT_XML,
        CONSOLE,
        MARKDOWN
    }

    /**
     * Retourne le format de ce reporter.
     */
    Format getFormat();

    /**
     * Génère un rapport pour une suite de scénarios.
     *
     * @param suiteResult Résultat de la suite
     * @param outputPath  Chemin de sortie
     * @throws IOException En cas d'erreur d'écriture
     */
    void generateReport(TnrSuiteResult suiteResult, Path outputPath) throws IOException;

    /**
     * Génère un rapport pour un scénario unique.
     *
     * @param result     Résultat du scénario
     * @param outputPath Chemin de sortie
     * @throws IOException En cas d'erreur d'écriture
     */
    void generateReport(TnrResult result, Path outputPath) throws IOException;

    /**
     * Génère le rapport sous forme de String.
     *
     * @param suiteResult Résultat de la suite
     * @return Contenu du rapport
     */
    String generateReportAsString(TnrSuiteResult suiteResult);

    /**
     * Génère le rapport sous forme de String.
     *
     * @param result Résultat du scénario
     * @return Contenu du rapport
     */
    String generateReportAsString(TnrResult result);

    /**
     * Retourne l'extension de fichier recommandée.
     */
    default String getFileExtension() {
        return switch (getFormat()) {
            case JSON -> ".json";
            case HTML -> ".html";
            case JUNIT_XML -> ".xml";
            case MARKDOWN -> ".md";
            case CONSOLE -> ".txt";
        };
    }

    /**
     * Génère un nom de fichier basé sur l'executionId.
     */
    default String generateFileName(String executionId) {
        return "tnr-report-" + executionId + getFileExtension();
    }
}
