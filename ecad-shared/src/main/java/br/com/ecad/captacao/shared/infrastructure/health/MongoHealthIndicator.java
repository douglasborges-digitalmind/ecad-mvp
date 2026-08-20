package br.com.ecad.captacao.shared.infrastructure.health;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import com.mongodb.client.MongoClient;

/**
 * Health check do MongoDB — executa ping para verificar conectividade.
 * Substitui o antigo CosmosHealthIndicator.
 */
public class MongoHealthIndicator implements HealthIndicator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoHealthIndicator.class);

    private final MongoClient mongoClient;
    private final String databaseName;

    public MongoHealthIndicator(MongoClient mongoClient, String databaseName) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
    }

    @Override
    public Health health() {
        try {
            var db = mongoClient.getDatabase(databaseName);
            db.runCommand(new Document("ping", 1));
            return Health.up().withDetail("database", databaseName).build();
        } catch (Exception e) {
            LOGGER.warn("MongoDB health check falhou: {}", e.getMessage());
            return Health.down().withDetail("database", databaseName).withDetail("error", e.getMessage()).build();
        }
    }
}