package br.com.ecad.captacao.processingengine;

import java.time.Duration;
import java.util.LinkedHashMap;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.infrastructure.kafka.KafkaMessagePublisher;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Consumer Kafka que substitui o antigo EventHubConsumerService.
 * Usa commit manual de offset (equivalente ao checkpoint do Event Hubs).
 * Em modo local (LOCAL_DEVELOPMENT_ENABLED=true), o consumo é feito via LocalQueueConsumerService.
 */
@Component
class KafkaConsumerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerService.class);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);

    private final ProcessingEngineSettings settings;
    private final LocalDevelopmentSettings localDevelopment;
    private final CapturedDocumentHandler handler;
    private final ConsumerState state;
    private final boolean enabled;
    private Thread consumerThread;
    private KafkaConsumer<String, String> consumer;

    KafkaConsumerService(
        ProcessingEngineSettings settings,
        LocalDevelopmentSettings localDevelopment,
        CapturedDocumentHandler handler,
        ConsumerState state,
        @Value("${PROCESSING_ENGINE_CONSUMER_ENABLED:true}") boolean enabled) {
        this.settings = settings;
        this.localDevelopment = localDevelopment;
        this.handler = handler;
        this.state = state;
        this.enabled = enabled;
    }

    @PostConstruct
    void start() {
        if (!enabled || localDevelopment.enabled) {
            return;
        }
        if (settings.kafkaBootstrapServers().isBlank()) {
            LOGGER.warn("Kafka bootstrap servers nao configurados. Consumer cloud do ProcessingEngine desativado.");
            return;
        }

        var props = new java.util.HashMap<String, Object>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.kafkaBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, settings.kafkaConsumerGroup());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, settings.kafkaMaxPollIntervalMs());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, settings.kafkaMaxPollRecords());

        if (!settings.kafkaSecurityProtocol().isBlank()) {
            props.put("security.protocol", settings.kafkaSecurityProtocol());
            if (!settings.kafkaSaslMechanism().isBlank()) {
                props.put("sasl.mechanism", settings.kafkaSaslMechanism());
            }
            var jaasConfig = KafkaMessagePublisher.resolveJaasConfigPublic(
                settings.kafkaSaslJaasConfig(),
                settings.kafkaSaslUsername(),
                settings.kafkaSaslPassword());
            if (jaasConfig != null && !jaasConfig.isBlank()) {
                props.put("sasl.jaas.config", jaasConfig);
            }
            LOGGER.info("Kafka SASL/SSL configurado: protocol={}, mechanism={}",
                settings.kafkaSecurityProtocol(), settings.kafkaSaslMechanism());
        }

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(java.util.List.of(settings.capturedDocumentsTopic()));

        consumerThread = new Thread(() -> {
            LOGGER.info("ProcessingEngine consumindo Kafka topic {} no consumer group {}.",
                settings.capturedDocumentsTopic(), settings.kafkaConsumerGroup());
            state.setConsumerRunning(true);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        var records = consumer.poll(POLL_TIMEOUT);
                        for (var record : records) {
                            var eventData = record.value();
                            var metadata = new LinkedHashMap<String, String>();
                            metadata.put("topic", record.topic());
                            metadata.put("consumer_group", settings.kafkaConsumerGroup());
                            metadata.put("partition", String.valueOf(record.partition()));
                            metadata.put("offset", String.valueOf(record.offset()));
                            var messageId = record.partition() + "-" + record.offset();
                            try {
                                handler.handle(eventData, messageId, metadata, () -> {
                                    try {
                                        consumer.commitSync();
                                    } catch (org.apache.kafka.clients.consumer.CommitFailedException commitEx) {
                                        LOGGER.warn("commit_offset_falhou_rebalance message_id={} - consumidor removido do grupo, commit sera refeito no rebalance",
                                            messageId);
                                    }
                                });
                            } catch (Exception ex) {
                                LOGGER.error("Falha ao processar mensagem Kafka sem checkpoint. message_id={}", messageId, ex);
                            }
                        }
                        if (!records.isEmpty()) {
                            try {
                                consumer.commitSync();
                            } catch (org.apache.kafka.clients.consumer.CommitFailedException commitEx) {
                                LOGGER.warn("commit_offset_falhou_rebalance - consumidor removido do grupo, commit sera refeito no rebalance");
                            }
                        }
                    } catch (org.apache.kafka.common.errors.WakeupException e) {
                        break;
                    } catch (Exception e) {
                        LOGGER.error("Erro no poll Kafka do ProcessingEngine.", e);
                    }
                }
            } finally {
                state.setConsumerRunning(false);
            }
        }, "kafka-consumer-processing-engine");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    @PreDestroy
    void stop() {
        state.setConsumerRunning(false);
        if (consumer != null) {
            consumer.wakeup();
            consumer.close();
        }
    }
}