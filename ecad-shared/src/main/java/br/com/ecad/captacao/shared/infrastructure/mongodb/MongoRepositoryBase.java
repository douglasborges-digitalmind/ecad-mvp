package br.com.ecad.captacao.shared.infrastructure.mongodb;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;

public abstract class MongoRepositoryBase<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoRepositoryBase.class);

    /**
     * Matches ISO-8601 strings produced by Jackson's JavaTimeModule:
     *   2026-08-17T16:00:00Z
     *   2026-08-17T16:00:00.123456789Z
     *   2026-08-17T16:00:00+01:00
     *   2026-08-17T13:00:00-03:00
     */
    private static final Pattern ISO_8601_PATTERN = Pattern.compile(
        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:\\d{2})$");

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    protected final MongoCollection<Document> collection;
    private final Class<T> itemType;

    protected MongoRepositoryBase(MongoClient client, String databaseName, String collectionName, Class<T> itemType) {
        this.collection = client.getDatabase(databaseName).getCollection(collectionName);
        this.itemType = itemType;
    }

    protected abstract String getPartitionKeyValue(T item);
    protected abstract String getId(T item);

    protected Optional<T> obterPorId(String id, String partitionKey) {
        var doc = collection.find(Filters.eq("_id", id)).first();
        if (doc == null) return Optional.empty();
        return Optional.of(fromDocument(doc));
    }

    protected T criarItem(T item) {
        var doc = toDocument(item);
        doc.put("_id", getId(item));
        collection.insertOne(doc);
        return item;
    }

    protected T atualizarItem(T item) {
        var doc = toDocument(item);
        doc.put("_id", getId(item));
        var opts = new ReplaceOptions().upsert(true);
        collection.replaceOne(Filters.eq("_id", getId(item)), doc, opts);
        return item;
    }

    protected void removerItem(String id, String partitionKey) {
        collection.deleteOne(Filters.eq("_id", id));
    }

    protected List<T> executarQuery(Bson filter) {
        return executarQuery(filter, null, true);
    }

    protected List<T> executarQuery(Bson filter, String partitionKey, boolean allowCrossPartition) {
        var startNanos = System.nanoTime();
        var results = new ArrayList<T>();
        try (var cursor = collection.find(filter).iterator()) {
            while (cursor.hasNext()) {
                results.add(fromDocument(cursor.next()));
            }
        }
        var elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

        if (partitionKey == null && LOGGER.isInfoEnabled()) {
            LOGGER.info("mongodb_query_full_scan collection={} elapsedMs={} results={}",
                collection.getNamespace().getCollectionName(), elapsedMs, results.size());
        }
        return results;
    }

    protected long executarScalarCount(Bson filter) {
        return collection.countDocuments(filter);
    }

    @SuppressWarnings("unchecked")
    protected T fromDocument(Document doc) {
        try {
            convertDatesToIsoStrings(doc);
            var json = doc.toJson();
            json = json.replaceFirst("\"_id\"", "\"id\"");
            var mapper = br.com.ecad.captacao.shared.JsonDefaults.objectMapper();
            return mapper.readValue(json, itemType);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao deserializar documento MongoDB para " + itemType.getSimpleName(), e);
        }
    }

    protected Document toDocument(T item) {
        try {
            var mapper = br.com.ecad.captacao.shared.JsonDefaults.objectMapper();
            var json = mapper.writeValueAsString(item);
            var doc = Document.parse(json);
            convertIsoStringsToDates(doc);
            return doc;
        } catch (Exception e) {
            throw new RuntimeException("Falha ao serializar " + itemType.getSimpleName() + " para documento MongoDB", e);
        }
    }

    /**
     * Recursively converts ISO-8601 strings into java.util.Date so MongoDB
     * stores them as BSON DateTime (epoch millis). This makes range queries
     * (Filters.lte / Filters.gte with OffsetDateTime) work correctly.
     */
    private static void convertIsoStringsToDates(Map<String, Object> map) {
        for (var entry : map.entrySet()) {
            entry.setValue(convertIsoStringsToDates(entry.getValue()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Object convertIsoStringsToDates(Object value) {
        if (value instanceof Map<?, ?> m) {
            convertIsoStringsToDates((Map<String, Object>) m);
            return m;
        }
        if (value instanceof List<?> list) {
            var result = new ArrayList<Object>(list.size());
            for (var item : list) {
                result.add(convertIsoStringsToDates(item));
            }
            return result;
        }
        if (value instanceof String s && ISO_8601_PATTERN.matcher(s).matches()) {
            try {
                var odt = OffsetDateTime.parse(s, ISO_FORMATTER);
                return Date.from(odt.toInstant());
            } catch (Exception ex) {
                return value;
            }
        }
        return value;
    }

    /**
     * Recursively converts java.util.Date values back to ISO-8601 strings
     * so Jackson can deserialize them into OffsetDateTime fields.
     */
    private static void convertDatesToIsoStrings(Map<String, Object> map) {
        for (var entry : map.entrySet()) {
            entry.setValue(convertDatesToIsoStrings(entry.getValue()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Object convertDatesToIsoStrings(Object value) {
        if (value instanceof Map<?, ?> m) {
            convertDatesToIsoStrings((Map<String, Object>) m);
            return m;
        }
        if (value instanceof List<?> list) {
            var result = new ArrayList<Object>(list.size());
            for (var item : list) {
                result.add(convertDatesToIsoStrings(item));
            }
            return result;
        }
        if (value instanceof Date d) {
            return OffsetDateTime.ofInstant(d.toInstant(), ZoneOffset.UTC)
                .format(ISO_FORMATTER);
        }
        return value;
    }
}