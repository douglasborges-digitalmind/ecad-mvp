package br.com.ecad.captacao.processingengine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;

@SpringBootTest(
    classes = EcadProcessingEngineApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        // Modo local: contextLoads valida apenas o wiring; o failfast cloud (ProcessingEngineSettings.validate)
        // exige conexoes Azure reais e nao pode ser exercitado em testes unitarios sem credenciais.
        "LOCAL_DEVELOPMENT_ENABLED=true",
        "PROCESSING_ENGINE_CONSUMER_ENABLED=false",
        "SGA_VERIFICATION_ENABLED=false",
        "AI_PROVIDER_CHAIN="
    })
class EcadProcessingEngineApplicationTest {
    @Test
    void contextLoads() {
    }

    @Test
    void fromEnvironmentReadsAiProviderSettings() {
        var environment = new MockEnvironment();
        environment.setProperty("ecad.processing-engine.ai-provider-chain", "openrouter,ollama");
        environment.setProperty("OPENROUTER_API_KEY", "token");
        environment.setProperty("OPENROUTER_MODEL", "nvidia/nemotron-nano-12b-v2-vl:free");

        var settings = ProcessingEngineSettings.fromEnvironment(environment);

        assertThat(settings.getAiProviders()).containsExactly("openrouter", "ollama");
        assertThat(settings.aiProvider().openRouterApiKey()).isEqualTo("token");
        assertThat(settings.aiProvider().openRouterModel()).isEqualTo("nvidia/nemotron-nano-12b-v2-vl:free");
    }
}
