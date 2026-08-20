package br.com.ecad.captacao.shared.infrastructure.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.UUID;

import br.com.ecad.captacao.shared.JsonDefaults;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LocalMessageQueue {
    private static final int MAX_DELIVERY_ATTEMPTS = 3;
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);
    private static final System.Logger LOGGER = System.getLogger(LocalMessageQueue.class.getName());

    private final LocalDevelopmentSettings settings;
    private final ObjectMapper mapper;

    public LocalMessageQueue(LocalDevelopmentSettings settings) {
        this.settings = settings;
        this.mapper = JsonDefaults.objectMapper();
    }

    public <T> Path enqueue(String topic, String route, T payload) throws IOException {
        var queuePath = settings.getQueuePath(topic, route);
        Files.createDirectories(queuePath);

        var fileName = FILE_TIMESTAMP.format(Instant.now()) + "-" + UUID.randomUUID().toString().replace("-", "") + ".json";
        var fullPath = queuePath.resolve(fileName);
        var json = mapper.writeValueAsString(payload);
        LocalFileOperations.writeStringAtomically(fullPath, json);
        return fullPath;
    }

    public <T> int consumeAvailable(String topic, String route, Class<T> payloadType, LocalMessageHandler<T> handler) throws Exception {
        return consumeAvailable(topic, route, payloadType, handler, 20);
    }

    public <T> int consumeAvailable(String topic, String route, Class<T> payloadType, LocalMessageHandler<T> handler, int maxMessages) throws Exception {
        var queuePath = settings.getQueuePath(topic, route);
        var processingPath = queuePath.resolve(".processing");
        var deadLetterPath = queuePath.resolve(".deadletter");

        Files.createDirectories(queuePath);
        Files.createDirectories(processingPath);
        Files.createDirectories(deadLetterPath);

        final java.util.List<Path> files;
        try (var stream = Files.list(queuePath)) {
            files = stream
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.naturalOrder())
                .limit(maxMessages)
                .toList();
        }

        var processed = 0;
        for (var file : files) {
            var processingFile = processingPath.resolve(file.getFileName());
            try {
                moveToProcessing(file, processingFile);
            } catch (IOException ex) {
                LOGGER.log(System.Logger.Level.WARNING, "Falha ao mover mensagem local para processamento: " + file, ex);
                continue;
            }

            try {
                var payload = mapper.readValue(processingFile.toFile(), payloadType);
                if (payload == null) {
                    throw new IllegalStateException("Payload invalido em " + processingFile);
                }

                handler.handle(payload);
                Files.deleteIfExists(processingFile);
                processed++;
            } catch (Exception ex) {
                var deliveryAttempts = getDeliveryAttempts(processingFile) + 1;
                var destinationFile = deliveryAttempts >= MAX_DELIVERY_ATTEMPTS
                    ? deadLetterPath.resolve(buildFileNameWithAttempts(processingFile.getFileName().toString(), deliveryAttempts))
                    : queuePath.resolve(buildFileNameWithAttempts(processingFile.getFileName().toString(), deliveryAttempts));

                Files.deleteIfExists(destinationFile);
                LocalFileOperations.moveAtomically(processingFile, destinationFile);
                throw ex;
            }
        }

        return processed;
    }

    private static int getDeliveryAttempts(Path filePath) {
        var fileName = removeExtension(filePath.getFileName().toString());
        var marker = "--attempt-";
        var markerIndex = fileName.toLowerCase().lastIndexOf(marker);
        if (markerIndex < 0) {
            return 0;
        }

        try {
            return Integer.parseInt(fileName.substring(markerIndex + marker.length()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void moveToProcessing(Path file, Path processingFile) throws IOException {
        LocalFileOperations.moveAtomically(file, processingFile);
    }

    private static String buildFileNameWithAttempts(String fileName, int attempts) {
        var extensionIndex = fileName.lastIndexOf('.');
        var extension = extensionIndex >= 0 ? fileName.substring(extensionIndex) : "";
        var baseName = extensionIndex >= 0 ? fileName.substring(0, extensionIndex) : fileName;
        var marker = "--attempt-";
        var markerIndex = baseName.toLowerCase().lastIndexOf(marker);
        if (markerIndex >= 0) {
            baseName = baseName.substring(0, markerIndex);
        }

        return attempts <= 0 ? baseName + extension : baseName + marker + attempts + extension;
    }

    private static String removeExtension(String fileName) {
        var extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex >= 0 ? fileName.substring(0, extensionIndex) : fileName;
    }
}
