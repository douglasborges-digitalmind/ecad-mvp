package br.com.ecad.captacao.controlcenter.models;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExecutarScrapingManualRequest(
    @JsonProperty("search_date_from") String searchDateFrom,
    @JsonProperty("search_date_to") String searchDateTo,
    @JsonProperty("search_max_results") Integer searchMaxResults,
    @JsonProperty("metadados") Map<String, String> metadados
) {
}
