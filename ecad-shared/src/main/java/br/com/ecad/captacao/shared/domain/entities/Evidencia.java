package br.com.ecad.captacao.shared.domain.entities;

import java.time.OffsetDateTime;

import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Evidencia(
    @JsonProperty("sequencia") int sequencia,
    @JsonProperty("tipo") TipoEvidencia tipo,
    @com.fasterxml.jackson.annotation.JsonAlias("url_origem") @JsonProperty("link_fonte") String urlOrigem,
    @JsonProperty("url_armazenamento_interno") String urlArmazenamentoInterno,
    @JsonProperty("data_captura") OffsetDateTime dataCaptura,
    @JsonProperty("hash_arquivo") String hashArquivo,
    @JsonProperty("json_bruto_url_interna") String jsonBrutoUrlInterna,
    @JsonProperty("evidencia_visual_url_interna") String evidenciaVisualUrlInterna,
    @JsonProperty("observacoes_ia") String observacoesIa
) {
    public Evidencia {
        if (urlOrigem == null) urlOrigem = "";
        if (urlArmazenamentoInterno == null) urlArmazenamentoInterno = "";
        if (hashArquivo == null) hashArquivo = "";
    }
}
