package br.com.ecad.captacao.controlcenter.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExecutarScrapingLotePncpResult(
    @JsonProperty("fontes_processadas") int fontesProcessadas,
    @JsonProperty("comandos_disparados") int comandosDisparados,
    @JsonProperty("canais_utilizados") int canaisUtilizados
) {
}