package br.com.ecad.captacao.shared.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TipoEvidencia implements JsonBackedEnum {
    CONTRATO_MUSICAL("contratoMusical");

    private final String jsonValue;

    TipoEvidencia(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static TipoEvidencia fromJson(String value) {
        return EnumJson.fromJson(TipoEvidencia.class, value);
    }

    @Override
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
