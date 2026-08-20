package br.com.ecad.captacao.shared.domain.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Documento(
    @JsonProperty("id") UUID id,
    @JsonProperty("url") String url,
    @JsonProperty("hash_conteudo") String hashConteudo,
    @JsonProperty("id_fonte_captacao") UUID idFonteCaptacao,
    @JsonProperty("tipo_evidencia") TipoEvidencia tipoEvidencia,
    @JsonProperty("url_staging") String urlStaging,
    @JsonProperty("nome_arquivo") String nomeArquivo,
    @JsonProperty("componente_origem") String componenteOrigem,
    @JsonProperty("criado_em") OffsetDateTime criadoEm
) {
    public Documento {
        if (id == null) id = UUID.randomUUID();
        if (url == null) url = "";
        if (hashConteudo == null) hashConteudo = "";
        if (urlStaging == null) urlStaging = "";
        if (nomeArquivo == null) nomeArquivo = "";
        if (componenteOrigem == null) componenteOrigem = "";
        if (criadoEm == null) criadoEm = OffsetDateTime.now();
    }
}
