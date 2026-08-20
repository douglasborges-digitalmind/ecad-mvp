package br.com.ecad.captacao.controlcenter.models;

import java.util.List;
import java.util.Map;

import br.com.ecad.captacao.shared.domain.enums.TipoCanal;
import br.com.ecad.captacao.shared.domain.enums.TipoFrequencia;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CriarFonteRequest(
    @JsonProperty("nome") String nome,
    @JsonProperty("unidade_ecad") String unidadeEcad,
    @JsonProperty("base_storage_path") String baseStoragePath,
    @JsonProperty("metadados") Map<String, String> metadados,
    @JsonProperty("canais_scraping") List<CriarCanalRequest> canaisScraping
) {
    public record CriarCanalRequest(
        @JsonProperty("id") java.util.UUID id,
        @JsonProperty("url") String url,
        @JsonProperty("instrucoes_scraping_ia") String instrucoesScrapingIa,
        @JsonProperty("palavras_chaves_busca") List<String> palavrasChavesBusca,
        @JsonProperty("metadados") Map<String, String> metadados,
        @JsonProperty("tipo") TipoCanal tipo,
        @JsonProperty("frequencia") FrequenciaRequest frequencia
    ) {
    }

    public record FrequenciaRequest(
        @JsonProperty("tipo") TipoFrequencia tipo,
        @JsonProperty("dias_da_semana") List<String> diasDaSemana,
        @JsonProperty("horario") String horario,
        @JsonProperty("intervalo_horas") Integer intervaloHoras
    ) {
    }
}
