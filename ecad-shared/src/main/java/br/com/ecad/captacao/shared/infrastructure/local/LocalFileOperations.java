package br.com.ecad.captacao.shared.infrastructure.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class LocalFileOperations {
    private LocalFileOperations() {
    }

    static void writeStringAtomically(Path target, String content) throws IOException {
        writeBytesAtomically(target, content.getBytes(StandardCharsets.UTF_8));
    }

    static void writeBytesAtomically(Path target, byte[] content) throws IOException {
        var directory = target.getParent();
        if (directory == null) {
            directory = Path.of(".");
        }
        Files.createDirectories(directory);

        var tempFile = Files.createTempFile(directory, tempPrefix(target), ".tmp");
        try {
            Files.write(tempFile, content);
            replaceAtomically(tempFile, target);
        } catch (IOException | RuntimeException ex) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
            throw ex;
        }
    }

    static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException | AccessDeniedException ex) {
            // Fallback: delete target first, then copy + delete source
            // (needed for Docker bind mounts on Windows where REPLACE_EXISTING also fails)
            Files.deleteIfExists(target);
            Files.copy(source, target);
            Files.deleteIfExists(source);
        }
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | AccessDeniedException ex) {
            // Fallback: delete target first, then copy + delete source
            // (needed for Docker bind mounts on Windows where REPLACE_EXISTING also fails)
            Files.deleteIfExists(target);
            Files.copy(source, target);
            Files.deleteIfExists(source);
        }
    }

    private static String tempPrefix(Path target) {
        var fileName = target.getFileName() == null ? "tmp" : target.getFileName().toString();
        return fileName.length() >= 3 ? fileName : "tmp" + fileName;
    }
}