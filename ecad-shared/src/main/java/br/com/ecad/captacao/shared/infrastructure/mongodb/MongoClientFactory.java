package br.com.ecad.captacao.shared.infrastructure.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.pojo.Conventions;
import org.bson.codecs.pojo.PojoCodecProvider;

/**
 * Fabrica centralizada de MongoClient com CodecRegistry que suporta java.time
 * (OffsetDateTime).
 *
 * O driver mongodb-driver-sync 5.x NAO registra codecs para java.time por padrao,
 * o que causa CodecConfigurationException ao serializar Document.toJson() quando
 * as entidades contem OffsetDateTime. Registrar o codec customizado OffsetDateTimeCodec
 * resolve isso. (O Jsr310CodecProvider foi removido no driver 5.x; Instant,
 * LocalDate, LocalDateTime, LocalTime, ZonedDateTime, Duration, Period NAO sao
 * usados por nenhuma entidade persistida.)
 */
public final class MongoClientFactory {
    private MongoClientFactory() {
    }

    public static MongoClient create(String connectionString) {
        var pojoCodecProvider = PojoCodecProvider.builder()
            .conventions(Conventions.DEFAULT_CONVENTIONS)
            .build();

        CodecRegistry registry = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromCodecs(new OffsetDateTimeCodec()),
            CodecRegistries.fromProviders(pojoCodecProvider));

        var settings = MongoClientSettings.builder()
            .applyConnectionString(new com.mongodb.ConnectionString(connectionString))
            .retryWrites(false)
            .retryReads(false)
            .codecRegistry(registry)
            .build();

        return MongoClients.create(settings);
    }
}
