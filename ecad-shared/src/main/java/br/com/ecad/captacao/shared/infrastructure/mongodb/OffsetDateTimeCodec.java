package br.com.ecad.captacao.shared.infrastructure.mongodb;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.bson.BsonDateTime;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

/**
 * Codec customizado para {@link OffsetDateTime} compatível com BSON 5.x.
 *
 * Serializa como {@link BsonDateTime} (epoch millis UTC).
 * Desserializa assumindo UTC ({@link ZoneOffset#UTC}).
 */
public final class OffsetDateTimeCodec implements Codec<OffsetDateTime> {

    @Override
    public void encode(BsonWriter writer, OffsetDateTime value, EncoderContext encoderContext) {
        writer.writeDateTime(value.toInstant().toEpochMilli());
    }

    @Override
    public OffsetDateTime decode(BsonReader reader, DecoderContext decoderContext) {
        long millis = reader.readDateTime();
        return OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }

    @Override
    public Class<OffsetDateTime> getEncoderClass() {
        return OffsetDateTime.class;
    }
}