package br.com.ecad.captacao.shared.domain.entities;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MunicipioUnidade {
    @JsonProperty("id")
    public UUID id = UUID.randomUUID();

    @JsonProperty("uf")
    public String uf = "";

    @JsonProperty("municipio")
    public String municipio = "";

    @JsonProperty("unidade_ecad")
    public String unidadeEcad = "";
}
