package br.com.ecad.captacao.shared.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TipoFonte implements JsonBackedEnum {
    PREFEITURA("prefeitura"),
    SECRETARIA_CULTURA("secretariaCultura"),
    OUTRO("outro");

    private final String jsonValue;

    TipoFonte(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static TipoFonte fromJson(String value) {
        return EnumJson.fromJson(TipoFonte.class, value);
    }

    @Override
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
