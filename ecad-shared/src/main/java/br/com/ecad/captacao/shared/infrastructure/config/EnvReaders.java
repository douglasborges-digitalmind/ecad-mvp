package br.com.ecad.captacao.shared.infrastructure.config;

import org.springframework.core.env.Environment;

/**
 * Utilitário centralizado para leitura de variáveis de ambiente.
 * Elimina duplicação de helpers `read`/`readInt`/`split` nos `*Settings`.
 */
public final class EnvReaders {

    private EnvReaders() {
    }

    /**
     * Lê uma propriedade do ambiente com fallback (sem nome legado).
     */
    public static String read(Environment environment, String key, String fallback) {
        return read(environment, key, key, fallback);
    }

    /**
     * Lê uma propriedade do ambiente com fallback e suporte a nome legado.
     *
     * @param environment ambiente Spring
     * @param key chave da propriedade (ex: "ecad.processing-engine.kafka-bootstrap-servers")
     * @param legacyEnvName nome da variável de ambiente legada (ex: "KAFKA_BOOTSTRAP_SERVERS")
     * @param fallback valor padrão
     * @return valor da propriedade ou fallback
     */
    public static String read(Environment environment, String key, String legacyEnvName, String fallback) {
        if (environment == null) {
            return fallback;
        }
        return environment.getProperty(key, environment.getProperty(legacyEnvName, fallback));
    }

    /**
     * Lê uma propriedade inteira do ambiente com fallback (sem nome legado).
     */
    public static int readInt(Environment environment, String key, int fallback) {
        return readInt(environment, key, key, fallback);
    }

    /**
     * Lê uma propriedade inteira do ambiente com fallback e suporte a nome legado.
     *
     * @param environment ambiente Spring
     * @param key chave da propriedade
     * @param legacyEnvName nome da variável de ambiente legada
     * @param fallback valor padrão
     * @return valor inteiro ou fallback
     */
    public static int readInt(Environment environment, String key, String legacyEnvName, int fallback) {
        try {
            return Integer.parseInt(read(environment, key, legacyEnvName, Integer.toString(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Lê uma propriedade longa do ambiente com fallback (sem nome legado).
     */
    public static long readLong(Environment environment, String key, long fallback) {
        try {
            return Long.parseLong(read(environment, key, Long.toString(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Lê uma propriedade longa do ambiente com fallback e suporte a nome legado.
     */
    public static long readLong(Environment environment, String key, String legacyEnvName, long fallback) {
        try {
            return Long.parseLong(read(environment, key, legacyEnvName, Long.toString(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Lê uma propriedade booleana do ambiente com fallback e suporte a nome legado.
     *
     * @param environment ambiente Spring
     * @param key chave da propriedade
     * @param legacyEnvName nome da variável de ambiente legada
     * @param fallback valor padrão
     * @return valor booleano ou fallback
     */
    public static boolean readBoolean(Environment environment, String key, String legacyEnvName, boolean fallback) {
        String value = read(environment, key, legacyEnvName, Boolean.toString(fallback));
        return Boolean.parseBoolean(value);
    }

    /**
     * Divide uma string por vírgula, trim e filtra vazios.
     *
     * @param value string a dividir
     * @return array de strings não vazias
     */
    public static String[] split(String value) {
        if (value == null || value.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toArray(String[]::new);
    }
}