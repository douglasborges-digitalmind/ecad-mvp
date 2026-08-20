package br.com.ecad.captacao.shared.common;

/**
 * Utilitarios minimos para manipulacao defensiva de Strings em integracoes externas,
 * substituindo metodos privados duplicados ao longo de varios servicos.
 */
public final class Strings {

    private Strings() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
