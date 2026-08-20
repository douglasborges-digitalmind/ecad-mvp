package br.com.ecad.captacao.controlcenter.models;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AtualizarFonteRequest(
    @JsonProperty("nome") String nome,
    @JsonProperty("unidade_ecad") String unidadeEcad,
    @JsonProperty("base_storage_path") String baseStoragePath,
    @JsonProperty("metadados") Map<String, String> metadados,
    @JsonProperty("canais_scraping") List<CriarFonteRequest.CriarCanalRequest> canaisScraping
) {
}
