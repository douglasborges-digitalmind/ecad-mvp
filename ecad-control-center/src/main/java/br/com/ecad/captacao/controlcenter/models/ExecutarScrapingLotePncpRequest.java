package br.com.ecad.captacao.controlcenter.models;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExecutarScrapingLotePncpRequest(
    @JsonProperty("offset") Integer offset,
    @JsonProperty("limite") Integer limite,
    @JsonProperty("uf") String uf,
    @JsonProperty("unidade_ecad") String unidadeEcad,
    @JsonProperty("search_date_from") String searchDateFrom,
    @JsonProperty("search_date_to") String searchDateTo,
    @JsonProperty("search_max_results") Integer searchMaxResults,
    @JsonProperty("metadados") Map<String, String> metadados
) {
}