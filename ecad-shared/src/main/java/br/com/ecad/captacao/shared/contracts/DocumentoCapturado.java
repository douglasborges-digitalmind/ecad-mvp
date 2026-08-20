package br.com.ecad.captacao.shared.contracts;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import com.fasterxml.jackson.annotation.JsonProperty;

public record DocumentoCapturado(
    @JsonProperty("id") UUID id,
    @JsonProperty("url_origem") String urlOrigem,
    @JsonProperty("url_staging_interno") String urlStagingInterno,
    @JsonProperty("instrucoes_captura") String instrucoesCaptura,
    @JsonProperty("hash_conteudo") String hashConteudo,
    @JsonProperty("id_fonte_captacao") UUID idFonteCaptacao,
    @JsonProperty("tipo") TipoEvidencia tipo,
    @JsonProperty("metadados") Map<String, String> metadados,
    @JsonProperty("timestamp") OffsetDateTime timestamp
) {
    /**
     * Compact canonical constructor.
     * Garante que {@code id} nunca seja {@code null} — seja via {@code new} ou desserializacao Jackson.
     * Se o JSON do Event Hub nao contiver o campo "id", um UUID novo e gerado como fallback,
     * mantendo a consistencia com o registro pre-existente no Cosmos (criado pelo scraper com o mesmo UUID).
     */
    public DocumentoCapturado {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}