package br.com.ecad.captacao.sgastatussync;

import java.util.ArrayList;

import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "ecad.sga-status-sync")
@Validated
public record SgaStatusSyncSettings(
    @DefaultValue("https://api-prd.ecad.org.br/oauth-rfc/v1/access-token") String sgaOAuthUrl,
    @DefaultValue("https://api-prd.ecad.org.br/show/v2") String sgaBaseUrl,
    @DefaultValue("") String sgaClientId,
    @DefaultValue("") String sgaClientSecret,
    @DefaultValue("") String sgaAuthorization,
    @DefaultValue("") String sgaUser,
    @DefaultValue("") String municipioCsvPath,
    @DefaultValue("true") boolean sgaVerificationEnabled,
    @DefaultValue("30") int sgaTimeoutSeconds,
    @DefaultValue("3") int sgaMaxRetries,
    @DefaultValue("150") int rateLimitDelayMs,
    @DefaultValue("600") int sgaResultLimit,
    @DefaultValue("4") int concurrency,
    @DefaultValue("") String mongoConnectionString,
    @DefaultValue("ecad-captacao") String mongoDatabaseName
) {
    /**
     * Failfast no boot quando rodando em modo cloud (LOCAL_DEVELOPMENT_ENABLED=false): exige
     * MONGODB_CONNECTION_STRING e, quando sgaVerificationEnabled, as credenciais OAuth do SGA
     * (SGA_CLIENT_ID / SGA_CLIENT_SECRET).
     */
    public void validate(LocalDevelopmentSettings localDevelopment) {
        if (localDevelopment.enabled) {
            return;
        }
        var missing = new ArrayList<String>();
        if (mongoConnectionString.isBlank()) {
            missing.add("MONGODB_CONNECTION_STRING");
        }
        if (sgaVerificationEnabled) {
            if (sgaClientId.isBlank()) {
                missing.add("SGA_CLIENT_ID");
            }
            if (sgaClientSecret.isBlank()) {
                missing.add("SGA_CLIENT_SECRET");
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Configuracoes obrigatorias ausentes: " + String.join(", ", missing));
        }
    }
}
