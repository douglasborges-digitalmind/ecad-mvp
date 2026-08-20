package br.com.ecad.captacao.shared.contracts;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ExecutarScraping(
    @JsonProperty("url_alvo") String urlAlvo,
    @JsonProperty("tipo_alvo") TipoEvidencia tipoAlvo,
    @JsonProperty("instrucoes_scraping_ia") String instrucoesScrapingIa,
    @JsonProperty("palavras_chaves_busca") List<String> palavrasChavesBusca,
    @JsonProperty("staging_path_storage") String stagingPathStorage,
    @JsonProperty("id_fonte_captacao") UUID idFonteCaptacao,
    @JsonProperty("id_canal_scraping") UUID idCanalScraping,
    @JsonProperty("metadados") Map<String, String> metadados
) {
}
