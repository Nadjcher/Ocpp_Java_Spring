package com.evse.simulator.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration OpenAPI/Swagger pour la documentation de l'API REST.
 *
 * Organisation en 2 groupes :
 * - Core API : Sessions, Smart Charging, Vehicles, OCPP
 * - Testing API : TNR, OCPI, Performance
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8877}")
    private int serverPort;

    /**
     * Configuration principale OpenAPI.
     */
    @Bean
    public OpenAPI evseSimulatorOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(servers())
                .tags(tags())
                .components(new Components());
    }

    /**
     * Groupe Core API - Fonctionnalités principales.
     */
    @Bean
    public GroupedOpenApi coreApi() {
        return GroupedOpenApi.builder()
                .group("1-core")
                .displayName("Core API")
                .pathsToMatch(
                        "/api/sessions/**",
                        "/api/smart-charging/**",
                        "/api/vehicles/**",
                        "/api/ocpp/**",
                        "/api/health/**"
                )
                .build();
    }

    /**
     * Groupe Testing API - Tests et Performance.
     */
    @Bean
    public GroupedOpenApi testingApi() {
        return GroupedOpenApi.builder()
                .group("2-testing")
                .displayName("Testing API")
                .pathsToMatch(
                        "/api/tnr/**",
                        "/api/ocpi/**",
                        "/api/performance/**",
                        "/api/batch/**"
                )
                .build();
    }

    /**
     * Informations sur l'API.
     */
    private Info apiInfo() {
        return new Info()
                .title("EVSE Simulator API")
                .version("2.0.0")
                .description("""
                        API pour le simulateur EVSE - Gestion des sessions de charge,
                        protocole OCPP 1.6, Smart Charging, TNR et tests OCPI.

                        ## Fonctionnalites principales
                        - **Sessions** : Creation et gestion des sessions de charge
                        - **OCPP** : Communication OCPP 1.6 avec le CSMS
                        - **Smart Charging** : Profils de charge et limitations
                        - **TNR** : Tests Non Regressifs automatises
                        - **OCPI** : Tests d'interoperabilite avec partenaires

                        ## WebSocket
                        - Endpoint STOMP : `/ws` (avec SockJS) ou `/ws-native`
                        - Topics : `/topic/sessions/{id}`, `/topic/metrics`

                        ## Codes de reponse standards
                        | Code | Description |
                        |------|-------------|
                        | 200 | Succes |
                        | 201 | Cree |
                        | 400 | Parametres invalides |
                        | 404 | Ressource non trouvee |
                        | 409 | Conflit |
                        | 500 | Erreur serveur |
                        """)
                .contact(new Contact()
                        .name("EVSE Simulator Team")
                        .email("support@evse-simulator.com"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    /**
     * Serveurs disponibles.
     */
    private List<Server> servers() {
        return List.of(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Local Development"),
                new Server()
                        .url("http://localhost:8877")
                        .description("Default Port")
        );
    }

    /**
     * Tags pour organiser les endpoints par domaine fonctionnel.
     */
    private List<Tag> tags() {
        return List.of(
                // Core
                new Tag()
                        .name("Sessions")
                        .description("Gestion des sessions de charge EVSE - CRUD et actions"),
                new Tag()
                        .name("OCPP")
                        .description("Operations protocole OCPP 1.6 - Messages et commandes"),
                new Tag()
                        .name("Smart Charging")
                        .description("Profils de charge intelligente - SetChargingProfile, GetCompositeSchedule"),
                new Tag()
                        .name("Vehicles")
                        .description("Profils de vehicules electriques avec courbes de charge"),
                new Tag()
                        .name("Health")
                        .description("Etat de sante de l'application"),

                // Testing
                new Tag()
                        .name("TNR")
                        .description("Tests Non Regressifs - Scenarios et executions"),
                new Tag()
                        .name("OCPI")
                        .description("Tests d'interoperabilite OCPI avec partenaires roaming"),
                new Tag()
                        .name("Performance")
                        .description("Tests de charge et metriques temps reel"),
                new Tag()
                        .name("Batch")
                        .description("Operations en lot sur les sessions")
        );
    }
}
