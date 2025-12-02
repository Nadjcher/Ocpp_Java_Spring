package com.evse.simulator.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration OpenAPI/Swagger pour la documentation de l'API REST.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
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
     * Informations sur l'API.
     */
    private Info apiInfo() {
        return new Info()
                .title("EVSE Simulator API")
                .version("1.0.0")
                .description("""
                        API REST du simulateur EVSE (Electric Vehicle Supply Equipment).

                        ## Fonctionnalités
                        - **Sessions** : Gestion des sessions de charge
                        - **OCPP** : Communication protocole OCPP 1.6
                        - **Véhicules** : Profils de véhicules électriques
                        - **Smart Charging** : Profils de charge intelligente
                        - **Performance** : Tests de charge et métriques
                        - **TNR** : Tests non-régressifs

                        ## WebSocket
                        Endpoint STOMP : `/ws` (avec SockJS) ou `/ws-native`

                        Topics disponibles :
                        - `/topic/sessions/{id}` : Mises à jour session
                        - `/topic/sessions/{id}/logs` : Logs temps réel
                        - `/topic/sessions/{id}/ocpp` : Messages OCPP
                        - `/topic/metrics` : Métriques globales
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
        return Arrays.asList(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Serveur local de développement"),
                new Server()
                        .url("http://localhost:8080")
                        .description("Serveur par défaut")
        );
    }

    /**
     * Tags pour organiser les endpoints.
     */
    private List<Tag> tags() {
        return Arrays.asList(
                new Tag().name("Sessions").description("Gestion des sessions de charge EVSE"),
                new Tag().name("OCPP").description("Opérations protocole OCPP 1.6"),
                new Tag().name("Vehicles").description("Gestion des profils de véhicules"),
                new Tag().name("Smart Charging").description("Gestion des profils de charge intelligente"),
                new Tag().name("Performance").description("Tests de performance et métriques"),
                new Tag().name("TNR").description("Tests non-régressifs et scénarios"),
                new Tag().name("Batch").description("Opérations en lot sur les sessions"),
                new Tag().name("Health").description("État de santé de l'application")
        );
    }
}