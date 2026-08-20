package br.com.ecad.captacao.shared.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TipoCanal implements JsonBackedEnum {
    AGREGADOR_GOV("agregadorGov");

    private final String jsonValue;

    TipoCanal(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static TipoCanal fromJson(String value) {
        return EnumJson.fromJson(TipoCanal.class, value);
    }

    @Override
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
