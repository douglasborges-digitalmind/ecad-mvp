package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.quarantine.BlobFailedMessageSink;
import br.com.ecad.captacao.shared.infrastructure.quarantine.FailedMessageSink;
import br.com.ecad.captacao.shared.infrastructure.quarantine.LocalFailedMessageSink;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class FailedMessageSinkConfiguration {
    @Bean
    FailedMessageSink failedMessageSink(LocalDevelopmentSettings localDevelopment, ProcessingCloudClients cloudClients) {
        return localDevelopment.enabled || !cloudClients.hasBlobStorage()
            ? new LocalFailedMessageSink(localDevelopment.rootPath)
            : new BlobFailedMessageSink(cloudClients.blobStorage());
    }
}