package br.com.ecad.captacao.controlcenter.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SetupPncpUrlsResult(
    @JsonProperty("arquivo_csv_entrada") String arquivoCsvEntrada,
    @JsonProperty("arquivo_csv_saida") String arquivoCsvSaida,
    @JsonProperty("conteudo_csv_saida") String conteudoCsvSaida,
    @JsonProperty("processadas") int processadas,
    @JsonProperty("sucessos") int sucessos,
    @JsonProperty("erros") int erros,
    @JsonProperty("avisos") List<String> avisos,
    @JsonProperty("resultados") List<SetupPncpUrlsItemResult> resultados
) {
}