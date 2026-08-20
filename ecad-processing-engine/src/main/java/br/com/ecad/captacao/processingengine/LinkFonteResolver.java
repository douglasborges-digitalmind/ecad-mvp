package br.com.ecad.captacao.processingengine;

import java.nio.charset.StandardCharsets;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
class LinkFonteResolver {
    private final BlobStorageService blobStorage;

    LinkFonteResolver(BlobStorageService blobStorage) {
        this.blobStorage = blobStorage;
    }

    String resolve(DocumentoCapturado documento, String urlArmazenamento) {
        var linkFromMetadata = documento.metadados() == null ? null : documento.metadados().get("link_fonte");
        if (linkFromMetadata != null && !linkFromMetadata.isBlank()) {
            return linkFromMetadata;
        }

        try {
            var download = blobStorage.download(urlArmazenamento);
            var contentType = download.contentType() == null ? "" : download.contentType().toLowerCase(java.util.Locale.ROOT);
            if (!contentType.contains("json") && !urlArmazenamento.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                return documento.urlOrigem();
            }
            JsonNode node = JsonDefaults.objectMapper().readTree(new String(download.content(), StandardCharsets.UTF_8));
            var link = node.path("link_fonte").asText(null);
            return link == null || link.isBlank() ? documento.urlOrigem() : link;
        } catch (Exception ex) {
            return documento.urlOrigem();
        }
    }
}
