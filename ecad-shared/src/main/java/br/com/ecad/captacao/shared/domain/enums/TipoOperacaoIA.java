package br.com.ecad.captacao.shared.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TipoOperacaoIA implements JsonBackedEnum {
    DISCOVERY_LINKS("discoveryLinks"),
    NAVEGACAO_FASE("navegacaoFase"),
    EXTRACAO_SEMANTICA("extracaoSemantica");

    private final String jsonValue;

    TipoOperacaoIA(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static TipoOperacaoIA fromJson(String value) {
        return EnumJson.fromJson(TipoOperacaoIA.class, value);
    }

    @Override
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
