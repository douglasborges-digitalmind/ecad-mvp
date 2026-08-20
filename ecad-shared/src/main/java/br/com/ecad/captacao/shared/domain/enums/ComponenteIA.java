package br.com.ecad.captacao.shared.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ComponenteIA implements JsonBackedEnum {
    DOCUMENT_SCRAPER("documentScraper"),
    PROCESSING_ENGINE("processingEngine");

    private final String jsonValue;

    ComponenteIA(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static ComponenteIA fromJson(String value) {
        return EnumJson.fromJson(ComponenteIA.class, value);
    }

    @Override
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
