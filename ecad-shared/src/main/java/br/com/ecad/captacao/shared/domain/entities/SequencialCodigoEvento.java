package br.com.ecad.captacao.shared.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SequencialCodigoEvento {
    @JsonProperty("id")
    public String id = "";

    @JsonProperty("ano")
    public int ano;

    @JsonProperty("ultimo_sequencial")
    public int ultimoSequencial;

    @JsonIgnore
    public String eTag;

    public static String gerarId(int ano) {
        return "sequencia_codigo_evento_" + ano;
    }
}