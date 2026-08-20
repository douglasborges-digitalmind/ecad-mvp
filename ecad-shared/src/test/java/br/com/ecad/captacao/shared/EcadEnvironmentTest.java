package br.com.ecad.captacao.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EcadEnvironmentTest {
    @Test
    void cloudSettingsShouldKeepCloudAgnosticEnvironmentNames() {
        assertThat(EcadEnvironment.CLOUD_SETTINGS)
            .contains(
                "MONGODB_CONNECTION_STRING",
                "MONGODB_DATABASE_NAME",
                "KAFKA_BOOTSTRAP_SERVERS",
                "AZURE_STORAGE_CONNECTION_STRING"
            );
    }

}