package br.com.ecad.captacao.shared.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StatusSGA implements JsonBackedEnum {
    JA_CADASTRADO("jaCadastrado"),
    INEDITO("inedito"),
    NAO_VERIFICADO("naoVerificado");

    private final String jsonValue;

    StatusSGA(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static StatusSGA fromJson(String value) {
        return EnumJson.fromJson(StatusSGA.class, value);
    }

    @Override
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
