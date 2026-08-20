package br.com.ecad.captacao.shared.infrastructure.local;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import br.com.ecad.captacao.shared.infrastructure.blob.BlobDownload;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorage;

public class LocalBlobStorage implements BlobStorage {
    private final LocalDevelopmentSettings settings;

    public LocalBlobStorage(LocalDevelopmentSettings settings) {
        this.settings = settings;
    }

    @Override
    public String upload(byte[] content, String relativePath, String contentType) throws IOException {
        var normalizedRelativePath = normalizeRelativePath(relativePath);
        var fullPath = settings.getBlobContainerPath().resolve(normalizedRelativePath);
        var directory = fullPath.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }

        LocalFileOperations.writeBytesAtomically(fullPath, content);
        return fullPath.toUri().toString();
    }

    @Override
    public BlobDownload download(String blobUrl) throws IOException {
        var fullPath = getFullPath(blobUrl);
        return new BlobDownload(Files.readAllBytes(fullPath), guessContentType(fullPath));
    }

    @Override
    public String move(String blobUrl, String fromPrefix, String toPrefix) throws IOException {
        var sourcePath = getFullPath(blobUrl);
        var containerFull = settings.getBlobContainerPath().toAbsolutePath().normalize();
        var sourceFull = sourcePath.toAbsolutePath().normalize();
        var relativePath = toSlashPath(containerFull.relativize(sourceFull));

        var normalizedFromPrefix = trimSlashes(toSlashPath(Path.of(fromPrefix.replace('\\', '/'))));
        var normalizedToPrefix = trimSlashes(toSlashPath(Path.of(toPrefix.replace('\\', '/'))));

        String destinationRelative;
        if (!relativePath.equalsIgnoreCase(normalizedFromPrefix) && !relativePath.toLowerCase().startsWith((normalizedFromPrefix + "/").toLowerCase())) {
            destinationRelative = normalizedToPrefix + "/" + sourceFull.getFileName();
        } else {
            var suffix = relativePath.length() == normalizedFromPrefix.length() ? "" : relativePath.substring(normalizedFromPrefix.length());
            destinationRelative = normalizedToPrefix + suffix;
        }

        var destinationPath = containerFull.resolve(normalizeRelativePath(destinationRelative));
        var directory = destinationPath.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }

        Files.move(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        return destinationPath.toUri().toString();
    }

    @Override
    public void delete(String blobUrl) throws IOException {
        Files.deleteIfExists(getFullPath(blobUrl));
    }

    @Override
    public boolean exists(String blobUrl) throws IOException {
        return Files.exists(getFullPath(blobUrl));
    }

    public String readTextIfExists(String relativePath) throws IOException {
        var fullPath = settings.rootPath.resolve(normalizeRelativePath(relativePath));
        return Files.exists(fullPath) ? Files.readString(fullPath, StandardCharsets.UTF_8) : null;
    }

    public void writeText(String relativePath, String content) throws IOException {
        var fullPath = settings.rootPath.resolve(normalizeRelativePath(relativePath));
        var directory = fullPath.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }

        LocalFileOperations.writeStringAtomically(fullPath, content);
    }

    public Path getFullPath(String blobUrl) {
        try {
            var uri = new URI(blobUrl);
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return Path.of(uri).toAbsolutePath().normalize();
            }
        } catch (IllegalArgumentException | URISyntaxException ignored) {
        }

        var decoded = URLDecoder.decode(blobUrl, StandardCharsets.UTF_8);
        var path = Path.of(decoded.replace('\\', '/'));
        return path.isAbsolute()
            ? path.toAbsolutePath().normalize()
            : settings.getBlobContainerPath().resolve(normalizeRelativePath(decoded)).toAbsolutePath().normalize();
    }

    private static Path normalizeRelativePath(String relativePath) {
        return Path.of(relativePath.replace('\\', '/').replaceAll("^/+", ""));
    }

    private static String toSlashPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String trimSlashes(String value) {
        return value.replaceAll("^/+|/+$", "");
    }

    private static String guessContentType(Path path) {
        var fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".json")) {
            return "application/json";
        }
        if (fileName.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }
}