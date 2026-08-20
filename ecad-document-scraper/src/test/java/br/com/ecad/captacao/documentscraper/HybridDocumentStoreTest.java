package br.com.ecad.captacao.documentscraper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.contracts.ExecutarScraping;
import br.com.ecad.captacao.shared.contracts.Routes;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.blob.DefaultBlobStorageService;
import br.com.ecad.captacao.shared.infrastructure.local.LocalBlobStorage;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.local.LocalMessageQueue;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalDocumentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class HybridDocumentStoreTest {
    @TempDir
    java.nio.file.Path root;

    @Test
    void persisteDocumentoEmStagingPublicaMensagemESalvaRegistroLocal() throws Exception {
        var settings = DocumentScraperSettings.fromEnvironment(new MockEnvironment()
            .withProperty("DOCUMENT_SCRAPER_LOCAL_ROUTE", "document-scraper"));
        var localDevelopment = new LocalDevelopmentSettings(root, true);
        localDevelopment.blobContainerName = settings.azureBlobContainerName();
        var store = new LocalJsonFileStore(localDevelopment);
        var documentos = new LocalDocumentoRepository(store);
        var cloudClients = new DocumentScraperCloudClients(settings, localDevelopment);
        var documentStore = new HybridDocumentStore(
            documentos,
            new DefaultBlobStorageService(null, new LocalBlobStorage(localDevelopment), localDevelopment, "staging/", "producao/"),
            new DocumentScraperCapturedDocumentPublisher(settings, localDevelopment, new LocalMessageQueue(localDevelopment), cloudClients));
        var comando = comando();

        var persisted = documentStore.persistirDocumento(
            comando,
            "https://example.org/contrato.pdf",
            "contrato.pdf",
            "conteudo-pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            TipoEvidencia.CONTRATO_MUSICAL,
            "observacao",
            Map.of("origem", "teste"));

        assertThat(persisted).isTrue();
        assertThat(documentos.urlJaFoiProcessada("https://example.org/contrato.pdf")).isTrue();
        assertThat(Files.list(localDevelopment.getQueuePath(settings.capturedDocumentsTopic(), Routes.PROCESSING_ENGINE))
            .filter(path -> path.getFileName().toString().endsWith(".json"))).hasSize(1);
        var saved = store.readCollection("documentos", br.com.ecad.captacao.shared.domain.entities.Documento.class).getFirst();
        assertThat(saved.componenteOrigem()).isEqualTo("DocumentScraperHybrid");
        assertThat(saved.urlStaging()).startsWith("file:/");
        assertThat(documentStore.persistirDocumento(
            comando,
            "https://example.org/outro.pdf",
            "outro.pdf",
            "conteudo-pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            TipoEvidencia.CONTRATO_MUSICAL,
            null,
            null)).isFalse();

        cloudClients.close();
    }

    private static ExecutarScraping comando() {
        return new ExecutarScraping(
            "https://example.org",
            TipoEvidencia.CONTRATO_MUSICAL,
            "capturar",
            java.util.List.of(),
            "staging/teste",
            UUID.randomUUID(),
            UUID.randomUUID(),
            Map.of("timestamp", OffsetDateTime.now().toString()));
    }
}
