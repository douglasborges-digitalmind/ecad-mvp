package br.com.ecad.captacao.shared.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CobrancaIngresso implements JsonBackedEnum {
    SIM("sim"),
    NAO_GRATUITO("naoGratuito"),
    NAO_IDENTIFICADO("naoIdentificado");

    private final String jsonValue;

    CobrancaIngresso(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static CobrancaIngresso fromJson(String value) {
        return EnumJson.fromJson(CobrancaIngresso.class, value);
    }

    @Override
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
