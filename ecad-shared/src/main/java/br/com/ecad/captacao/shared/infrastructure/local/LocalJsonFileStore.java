package br.com.ecad.captacao.shared.infrastructure.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import br.com.ecad.captacao.shared.JsonDefaults;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LocalJsonFileStore {
    private static final ConcurrentHashMap<Path, Object> LOCKS = new ConcurrentHashMap<>();

    private final LocalDevelopmentSettings settings;
    private final ObjectMapper mapper;

    public LocalJsonFileStore(LocalDevelopmentSettings settings) {
        this.settings = settings;
        this.mapper = JsonDefaults.objectMapper().copy();
    }

    public <T> List<T> readCollection(String collectionName, Class<T> itemType) throws IOException {
        var path = getCollectionPath(collectionName);
        var gate = LOCKS.computeIfAbsent(path, ignored -> new Object());
        synchronized (gate) {
            return readUnsafe(path, itemType);
        }
    }

    public <T> void writeCollection(String collectionName, List<T> items) throws IOException {
        var path = getCollectionPath(collectionName);
        var gate = LOCKS.computeIfAbsent(path, ignored -> new Object());
        synchronized (gate) {
            writeUnsafe(path, items);
        }
    }

    public <T, TResult> TResult mutateCollection(String collectionName, Class<T> itemType, Function<List<T>, TResult> mutate) throws IOException {
        var path = getCollectionPath(collectionName);
        var gate = LOCKS.computeIfAbsent(path, ignored -> new Object());
        synchronized (gate) {
            var mutable = new ArrayList<>(readUnsafe(path, itemType));
            var result = mutate.apply(mutable);
            writeUnsafe(path, mutable);
            return result;
        }
    }

    private Path getCollectionPath(String collectionName) throws IOException {
        Files.createDirectories(settings.dataRootPath());
        return settings.dataRootPath().resolve(collectionName + ".json").toAbsolutePath().normalize();
    }

    private <T> List<T> readUnsafe(Path path, Class<T> itemType) throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0) {
            return List.of();
        }

        var json = Files.readString(path, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            return List.of();
        }

        var listType = mapper.getTypeFactory().constructCollectionType(ArrayList.class, itemType);
        return mapper.readValue(json, listType);
    }

    private <T> void writeUnsafe(Path path, List<T> items) throws IOException {
        var directory = path.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }

        var json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(items);
        LocalFileOperations.writeStringAtomically(path, json);
    }
}
