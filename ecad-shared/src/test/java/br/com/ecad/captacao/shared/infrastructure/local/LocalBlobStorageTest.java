package br.com.ecad.captacao.shared.infrastructure.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalBlobStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void uploadDownloadAndMoveShouldUseLocalContainerPaths() throws Exception {
        var settings = new LocalDevelopmentSettings(tempDir, true);
        var storage = new LocalBlobStorage(settings);

        var url = storage.upload("conteudo".getBytes(StandardCharsets.UTF_8), "staging-area/BA/Salvador/doc.txt", "text/plain");
        var download = storage.download(url);

        try (var stream = Files.walk(settings.getBlobContainerPath())) {
            assertThat(stream.map(path -> path.getFileName().toString()).toList())
                .noneMatch(name -> name.endsWith(".tmp"));
        }

        assertThat(new String(download.content(), StandardCharsets.UTF_8)).isEqualTo("conteudo");
        assertThat(download.contentType()).isEqualTo("text/plain");

        var movedUrl = storage.move(url, "staging-area", "production-area");

        assertThat(Files.exists(storage.getFullPath(url))).isFalse();
        assertThat(storage.getFullPath(movedUrl).toString()).contains("production-area");
        assertThat(new String(storage.download(movedUrl).content(), StandardCharsets.UTF_8)).isEqualTo("conteudo");
    }
}
