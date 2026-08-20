package br.com.ecad.captacao.processingengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.enums.ProviderIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorage;
import br.com.ecad.captacao.shared.infrastructure.local.LocalBlobStorage;
import br.com.ecad.captacao.shared.infrastructure.local.LocalMessageQueue;
import br.com.ecad.captacao.shared.infrastructure.repositories.DocumentoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    classes = {EcadProcessingEngineApplication.class, ProcessingEngineLocalConsumerTest.FakeAiConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "LOCAL_DEVELOPMENT_ENABLED=true",
        "PROCESSING_ENGINE_CONSUMER_ENABLED=false",
        "SGA_VERIFICATION_ENABLED=false"
    })
class ProcessingEngineLocalConsumerTest {
    @TempDir
    static java.nio.file.Path localRoot;

    @Autowired
    LocalMessageQueue queue;

    @Autowired
    BlobStorage blobStorage;

    @Autowired
    ProcessingEngineSettings settings;

    @Autowired
    LocalQueueConsumerService consumer;

    @Autowired
    DocumentoRepository documentos;

    @Autowired
    EventoRepository eventos;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("LOCAL_DEVELOPMENT_ROOT", () -> localRoot.toString());
    }

    @Test
    void consomeDocumentoCapturadoDaFilaLocalEPreservaRegistroDoDocumento() throws Exception {
        var idDocumento = UUID.randomUUID();
        var url = "https://municipio.example/contrato.pdf";
        var stagingUrl = blobStorage.upload(
            "Show da Virada em Salvador na Praca Central".getBytes(StandardCharsets.UTF_8),
            "staging/contrato.txt", "text/plain");
        var payload = new DocumentoCapturado(
            idDocumento,
            url,
            stagingUrl,
            "capturar contrato musical",
            "sha256-abc",
            UUID.randomUUID(),
            TipoEvidencia.CONTRATO_MUSICAL,
            Map.of("nome_arquivo", "contrato.pdf", "componente_origem", "document-scraper"),
            OffsetDateTime.parse("2024-01-02T03:04:05Z"));

        queue.enqueue(settings.capturedDocumentsTopic(), settings.localConsumerRoute(), payload);

        var processed = consumer.consumeOnce();

        assertThat(processed).isEqualTo(1);
        assertThat(documentos.urlJaFoiProcessada(url)).isTrue();
        assertThat(documentos.arquivoJaFoiProcessado("sha256-abc")).isTrue();
        assertThat(eventos.listar("Salvador", null, null, null, null, null, null, null)).hasSize(1);
    }

    @TestConfiguration
    static class FakeAiConfiguration {
        @Bean
        @Primary
        AiProviderChain fakeAiProviderChain() {
            return (prompt, mediaBytes, mimeType, tipoDocumento, idFonteCaptacao) -> new AiProviderChain.AiProviderExecution(
                new AiResponse(
                    "{\"evento_identificado\":true,\"titulo\":\"Show da Virada\",\"data_inicio\":\"2026-04-05T00:00:00Z\",\"local\":\"Praca Central\",\"municipio\":\"Salvador\",\"uf\":\"BA\",\"interpretes\":[\"Banda Solar\"]}",
                    10,
                    5,
                    "fake-model",
                    ProviderIA.GEMINI_NATIVO,
                    BigDecimal.ZERO),
                List.<MetricaExecucaoIA>of());
        }
    }
}