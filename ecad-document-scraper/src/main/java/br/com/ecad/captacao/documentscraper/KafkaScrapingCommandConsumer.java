package br.com.ecad.captacao.documentscraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KafkaScrapingCommandConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaScrapingCommandConsumer.class);

    private final DocumentScraperSettings settings;
    private final HybridScrapingPipeline pipeline;
    private final ObjectMapper objectMapper;

    public KafkaScrapingCommandConsumer(DocumentScraperSettings settings, HybridScrapingPipeline pipeline, ObjectMapper objectMapper) {
        this.settings = settings;
        this.pipeline = pipeline;
        this.objectMapper = objectMapper;
        start();
    }

    public void start() {
        new Thread(this::poll, "kafka-scraping-consumer").start();
    }

    private void poll() {
        var props = new Properties();
        props.put("bootstrap.servers", settings.kafkaBootstrapServers());
        props.put("group.id", settings.consumerGroup());
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "latest");
        props.put("enable.auto.commit", "false");
        props.put("max.poll.interval.ms", settings.kafkaMaxPollIntervalMs());
        props.put("max.poll.records", settings.kafkaMaxPollRecords());

        if (!settings.kafkaSecurityProtocol().isBlank()) {
            props.put("security.protocol", settings.kafkaSecurityProtocol());
            if (!settings.kafkaSaslMechanism().isBlank()) {
                props.put("sasl.mechanism", settings.kafkaSaslMechanism());
            }
            var jaasConfig = br.com.ecad.captacao.shared.infrastructure.kafka.KafkaMessagePublisher
                .resolveJaasConfigPublic(settings.kafkaSaslJaasConfig(),
                    settings.kafkaSaslUsername(), settings.kafkaSaslPassword());
            if (jaasConfig != null && !jaasConfig.isBlank()) {
                props.put("sasl.jaas.config", jaasConfig);
            }
            LOGGER.info("Kafka SASL/SSL configurado: protocol={}, mechanism={}",
                settings.kafkaSecurityProtocol(), settings.kafkaSaslMechanism());
        }

        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(Collections.singleton(settings.scrapingCommandsTopic()));
            LOGGER.info("KafkaScrapingCommandConsumer iniciado topico={} grupo={}",
                settings.scrapingCommandsTopic(), settings.consumerGroup());
            while (!Thread.currentThread().isInterrupted()) {
                var records = consumer.poll(Duration.ofMillis(1000));
                for (var record : records) {
                    LOGGER.info("Consumindo comando kafka topico={} offset={} key={}",
                        record.topic(), record.offset(), record.key());
                    // Commita o offset ANTES de processar (at-least-once semantics).
                    // Se o container reiniciar durante o processamento, o offset ja estara commitado
                    // e a mensagem nao sera reprocessada infinitamente.
                    try {
                        consumer.commitSync();
                    } catch (org.apache.kafka.clients.consumer.CommitFailedException commitEx) {
                        LOGGER.warn("commit_offset_falhou_pre_processamento topico={} offset={} - consumidor removido do grupo",
                            record.topic(), record.offset());
                    }
                    try {
                        var payload = record.value();
                        var comando = objectMapper.readValue(payload, br.com.ecad.captacao.shared.contracts.ExecutarScraping.class);
                        LOGGER.info("Executando scraping: urlAlvo={} tipoAlvo={} idFonte={}", 
                            comando.urlAlvo(), comando.tipoAlvo(), comando.idFonteCaptacao());
                        pipeline.processarComando(comando);
                    } catch (Exception ex) {
                        LOGGER.error("Erro ao processar comando Kafka: {}", record.value(), ex);
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Erro no KafkaScrapingCommandConsumer", ex);
        }
    }
}
