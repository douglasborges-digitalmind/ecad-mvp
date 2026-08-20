package br.com.ecad.captacao.shared.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StatusEvento implements JsonBackedEnum {
    AGENDADO("agendado"),
    EM_ANDAMENTO("emAndamento"),
    REALIZADO("realizado"),
    CANCELADO("cancelado");

    private final String jsonValue;

    StatusEvento(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static StatusEvento fromJson(String value) {
        return EnumJson.fromJson(StatusEvento.class, value);
    }

    @Override
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
