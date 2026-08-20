package br.com.ecad.captacao.shared.domain.enums;

import java.util.Arrays;

public final class EnumJson {
    private EnumJson() {
    }

    public static <T extends Enum<T> & JsonBackedEnum> T fromJson(Class<T> enumType, String value) {
        return Arrays.stream(enumType.getEnumConstants())
            .filter(item -> item.jsonValue().equals(value) || item.name().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Valor invalido para " + enumType.getSimpleName() + ": " + value));
    }
}
