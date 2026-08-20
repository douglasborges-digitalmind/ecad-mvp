package br.com.ecad.captacao.controlcenter.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SetupPncpUrlsItemResult(
    @JsonProperty("cnpj") String cnpj,
    @JsonProperty("municipio") String municipio,
    @JsonProperty("uf") String uf,
    @JsonProperty("unidade_ecad") String unidadeEcad,
    @JsonProperty("id_pncp") String idPncp,
    @JsonProperty("url_busca") String urlBusca,
    @JsonProperty("status") String status,
    @JsonProperty("erro") String erro
) {
}