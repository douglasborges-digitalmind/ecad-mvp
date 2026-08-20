package br.com.ecad.captacao.shared.infrastructure.local;

import java.nio.file.Path;

public class LocalDevelopmentSettings {
    public boolean enabled;
    public Path rootPath;
    public String blobContainerName = "captura-documentos";

    public LocalDevelopmentSettings(Path rootPath) {
        this.rootPath = rootPath.toAbsolutePath().normalize();
    }

    public LocalDevelopmentSettings(Path rootPath, boolean enabled) {
        this(rootPath);
        this.enabled = enabled;
    }

    public Path dataRootPath() {
        return rootPath.resolve("data");
    }

    public Path instancesRootPath() {
        return rootPath.resolve("instances");
    }

    public Path queueRootPath() {
        return rootPath.resolve("queues");
    }

    public Path storageRootPath() {
        return rootPath.resolve("storage");
    }

    public Path getServiceInstancesPath(String serviceName) {
        return instancesRootPath().resolve(serviceName);
    }

    public Path getQueuePath(String topic, String route) {
        return queueRootPath().resolve(topic).resolve(route);
    }

    public Path getBlobContainerPath() {
        return getBlobContainerPath(blobContainerName);
    }

    public Path getBlobContainerPath(String containerName) {
        return storageRootPath().resolve(containerName == null || containerName.isBlank() ? blobContainerName : containerName);
    }
}
