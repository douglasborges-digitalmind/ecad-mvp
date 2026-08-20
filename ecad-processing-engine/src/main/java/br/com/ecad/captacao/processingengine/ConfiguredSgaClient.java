package br.com.ecad.captacao.processingengine;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.common.RetryPolicy;
import br.com.ecad.captacao.shared.common.UriQueryBuilder;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.infrastructure.sga.SgaCredentialsProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class ConfiguredSgaClient implements SgaClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfiguredSgaClient.class);

    private final ProcessingEngineSettings settings;
    private final SgaHttpTransport transport;
    private final SgaCredentialsProvider credentialsProvider;
    private final RetryPolicy retryPolicy;

    ConfiguredSgaClient(ProcessingEngineSettings settings, SgaHttpTransport transport, SgaCredentialsProvider credentialsProvider) {
        this.settings = settings;
        this.transport = transport;
        this.credentialsProvider = credentialsProvider;
        this.retryPolicy = RetryPolicy.of(settings.sgaMaxRetries(), 1000, 30_000);
    }

    @Override
    public StatusSGA verificar(String titulo, String local, String dataInicio, String dataFim, String uf) {
        if (!settings.sgaVerificationEnabled()) {
            return StatusSGA.NAO_VERIFICADO;
        }

        var query = new LinkedHashMap<String, String>();
        query.put("nome", titulo);
        query.put("local", local);
        query.put("uf", uf);
        query.put("dataInicio", normalizeDate(dataInicio));
        var normalizedDataFim = normalizeDate(dataFim);
        query.put("dataFim", normalizedDataFim.isBlank() ? normalizeDate(dataInicio) : normalizedDataFim);

        try {
            return retryPolicy.execute(() -> {
                var token = credentialsProvider.getToken();
                var headers = new LinkedHashMap<String, String>();
                headers.put("Authorization", "Bearer " + token);
                if (!settings.sgaUser().isBlank()) {
                    headers.put("USER", settings.sgaUser());
                }
                var response = transport.send("GET", buildShowsUri(query), headers, "", timeout());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("SGA retornou HTTP " + response.statusCode());
                }
                var payload = JsonDefaults.objectMapper().readTree(response.body());
                return resultSize(payload) > 0 ? StatusSGA.JA_CADASTRADO : StatusSGA.INEDITO;
            }, LOGGER, "SGA consulta");
        } catch (Exception ex) {
            LOGGER.error("Falha persistente ao consultar o SGA.", ex);
            return StatusSGA.NAO_VERIFICADO;
        }
    }

    private Duration timeout() {
        return Duration.ofSeconds(Math.max(1, settings.sgaTimeoutSeconds()));
    }

    private static int resultSize(JsonNode payload) {
        var value = payload.path("resultSize");
        if (value.isNumber()) {
            return value.asInt();
        }
        try {
            return Integer.parseInt(value.asText("0"));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String normalizeDate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() >= 10 ? value.substring(0, 10) : value;
    }

    private URI buildShowsUri(LinkedHashMap<String, String> query) {
        var builder = UriQueryBuilder.from(settings.sgaBaseUrl(), "/shows");
        for (var entry : query.entrySet()) {
            builder.param(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }
}
