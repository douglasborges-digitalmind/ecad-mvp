package br.com.ecad.captacao.shared.infrastructure.quarantine;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorage;

public class BlobFailedMessageSink implements FailedMessageSink {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final BlobStorage blobStorage;

    public BlobFailedMessageSink(BlobStorage blobStorage) {
        this.blobStorage = blobStorage;
    }

    @Override
    public void store(String component, String messageId, String payload, String reason, FailedMessageContext context) throws Exception {
        var path = "failed-messages/" + LocalFailedMessageSink.sanitize(component) + "/"
            + FILE_TIMESTAMP.format(OffsetDateTime.now(ZoneOffset.UTC)) + "_" + LocalFailedMessageSink.sanitize(messageId) + ".json";
        var envelope = LocalFailedMessageSink.envelope(component, messageId, payload, reason, context);
        blobStorage.upload(JsonDefaults.objectMapper().writeValueAsString(envelope).getBytes(StandardCharsets.UTF_8), path, "application/json");
    }
}