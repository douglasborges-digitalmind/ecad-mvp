package br.com.ecad.captacao.shared.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TipoMusica implements JsonBackedEnum {
    AO_VIVO("aoVivo"),
    MECANICA("mecanica"),
    MISTA("mista"),
    NAO_IDENTIFICADO("naoIdentificado");

    private final String jsonValue;

    TipoMusica(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static TipoMusica fromJson(String value) {
        return EnumJson.fromJson(TipoMusica.class, value);
    }

    @Override
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
