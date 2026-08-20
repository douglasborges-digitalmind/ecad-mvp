package br.com.ecad.captacao.loganalyser;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

class FonteCaptacaoResumo {
    @JsonProperty("id")
    public UUID id;

    @JsonProperty("nome")
    public String nome = "";

    @JsonProperty("unidade_ecad")
    public String unidadeEcad = "";

    @JsonProperty("base_storage_path")
    public String baseStoragePath = "";
}
