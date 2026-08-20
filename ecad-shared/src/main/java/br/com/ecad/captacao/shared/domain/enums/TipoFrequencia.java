package br.com.ecad.captacao.shared.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TipoFrequencia implements JsonBackedEnum {
    DIARIO("diario"),
    SEMANAL("semanal"),
    MENSAL("mensal"),
    PERSONALIZADO("personalizado");

    private final String jsonValue;

    TipoFrequencia(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static TipoFrequencia fromJson(String value) {
        return EnumJson.fromJson(TipoFrequencia.class, value);
    }

    @Override
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}