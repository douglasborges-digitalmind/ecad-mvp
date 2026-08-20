package br.com.ecad.captacao.shared.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProviderIA implements JsonBackedEnum {
    OPEN_ROUTER("openRouter"),
    GEMINI_NATIVO("geminiNativo"),
    OLLAMA("ollama"),
    AZURE_OPENAI("azureOpenAi");

    private final String jsonValue;

    ProviderIA(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static ProviderIA fromJson(String value) {
        return EnumJson.fromJson(ProviderIA.class, value);
    }

    @Override
    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
