package br.com.ecad.captacao.shared.infrastructure.health;

import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health check do Blob Storage — verifica conectividade via interface {@link BlobStorage}.
 */
public class BlobHealthIndicator implements HealthIndicator {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlobHealthIndicator.class);

    private final BlobStorage blobStorage;
    private final String label;

    public BlobHealthIndicator(BlobStorage blobStorage, String label) {
        this.blobStorage = blobStorage;
        this.label = label;
    }

    @Override
    public Health health() {
        try {
            var ok = blobStorage.exists("health-check.txt");
            return Health.up().withDetail("storage", label).withDetail("reachable", ok).build();
        } catch (Exception e) {
            LOGGER.warn("Blob Storage health check falhou ({}): {}", label, e.getMessage());
            return Health.down().withDetail("storage", label).withDetail("error", e.getMessage()).build();
        }
    }
}