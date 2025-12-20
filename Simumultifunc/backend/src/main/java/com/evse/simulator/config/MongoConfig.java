package com.evse.simulator.config;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Configuration MongoDB.
 * <p>
 * Active uniquement quand data.use-mongodb=true.
 * Cette configuration active les repositories MongoDB et importe
 * l'auto-configuration MongoDB qui est exclue par défaut.
 * </p>
 * <p>
 * Pour activer MongoDB:
 * - Définir USE_MONGODB=true
 * - S'assurer que MongoDB est démarré sur MONGODB_URI (default: localhost:27017)
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "data.use-mongodb", havingValue = "true", matchIfMissing = false)
@ImportAutoConfiguration({
    MongoAutoConfiguration.class,
    MongoDataAutoConfiguration.class,
    MongoRepositoriesAutoConfiguration.class
})
@EnableMongoRepositories(basePackages = "com.evse.simulator.repository.mongo")
public class MongoConfig {
    // La configuration MongoDB est automatiquement gérée par Spring Boot
    // Cette classe active simplement les repositories MongoDB de manière conditionnelle
}
