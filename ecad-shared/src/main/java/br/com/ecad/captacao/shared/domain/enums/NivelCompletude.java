package br.com.ecad.captacao.shared.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NivelCompletude implements JsonBackedEnum {
    ALTO("alto"),
    MEDIO("medio"),
    BASICO("basico"),
    INSUFICIENTE("insuficiente");

    private final String jsonValue;

    NivelCompletude(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static NivelCompletude fromJson(String value) {
        return EnumJson.fromJson(NivelCompletude.class, value);
    }

    @Override
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
