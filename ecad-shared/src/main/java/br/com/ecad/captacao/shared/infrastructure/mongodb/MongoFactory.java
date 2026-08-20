package br.com.ecad.captacao.shared.infrastructure.mongodb;

import com.mongodb.client.MongoClient;

/**
 * Factory para criar instâncias de {@link MongoClient}.
 * Centraliza a lógica de criação do cliente MongoDB.
 */
public final class MongoFactory {

    private MongoFactory() {
    }

    /**
     * Cria um MongoClient se a connection string estiver configurada.
     *
     * @param connectionString connection string do MongoDB (pode ser vazio)
     * @return MongoClient configurado ou null se não houver connection string
     */
    public static MongoClient create(String connectionString) {
        if (connectionString == null || connectionString.isBlank()) {
            return null;
        }
        return MongoClientFactory.create(connectionString);
    }
}