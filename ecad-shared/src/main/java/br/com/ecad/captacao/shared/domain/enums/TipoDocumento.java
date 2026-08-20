package br.com.ecad.captacao.shared.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TipoDocumento implements JsonBackedEnum {
    CONTRATO_MUSICAL("contratoMusical");

    private final String jsonValue;

    TipoDocumento(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static TipoDocumento fromJson(String value) {
        return EnumJson.fromJson(TipoDocumento.class, value);
    }

    @Override
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
