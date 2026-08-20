package br.com.ecad.captacao.shared.infrastructure.kafka;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.infrastructure.messaging.MessageConsumer;
import br.com.ecad.captacao.shared.infrastructure.messaging.MessagePublisher;

/**
 * Implementação Kafka de {@link MessagePublisher}.
 * <p>
 * Suporta autenticação SASL/SSL para Azure Event Hubs via Kafka protocol.
 * Quando {@code securityProtocol} é não-vazio (ex.: "SASL_SSL"), configura
 * {@code security.protocol}, {@code sasl.mechanism} e {@code sasl.jaas.config}.
 */
public class KafkaMessagePublisher implements MessagePublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaMessagePublisher.class);

    private final KafkaProducer<String, String> producer;

    public KafkaMessagePublisher(String bootstrapServers) {
        this(bootstrapServers, null, null, null, null, null);
    }

    public KafkaMessagePublisher(String bootstrapServers, String securityProtocol,
                                  String saslMechanism, String saslJaasConfig) {
        this(bootstrapServers, securityProtocol, saslMechanism, saslJaasConfig, null, null);
    }

    public KafkaMessagePublisher(String bootstrapServers, String securityProtocol,
                                  String saslMechanism, String saslJaasConfig,
                                  String saslUsername, String saslPassword) {
        LOGGER.info("KafkaMessagePublisher init: bootstrapServers={}, securityProtocol={}, saslMechanism={}, jaasConfigPresent={}, saslUsernamePresent={}, saslPasswordPresent={}",
                bootstrapServers,
                securityProtocol,
                saslMechanism,
                saslJaasConfig != null && !saslJaasConfig.isBlank(),
                saslUsername != null && !saslUsername.isBlank(),
                saslPassword != null && !saslPassword.isBlank());
        var props = new HashMap<String, Object>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        applySaslConfig(props, securityProtocol, saslMechanism, saslJaasConfig, saslUsername, saslPassword);
        this.producer = new KafkaProducer<>(props);
    }

    public KafkaMessagePublisher(KafkaProducer<String, String> producer) {
        this.producer = producer;
    }

    @Override
    public void publish(String topic, Object payload) throws IOException {
        try {
            var json = JsonDefaults.objectMapper().writeValueAsString(payload);
            producer.send(new ProducerRecord<>(topic, json)).get();
            LOGGER.info("Mensagem publicada no Kafka topic={} payloadSize={}", topic, json.length());
        } catch (Exception e) {
            throw new IOException("Falha ao publicar mensagem no Kafka topic=" + topic, e);
        }
    }

    public void close() {
        producer.close();
    }

    /**
     * Cria um {@link KafkaConsumer} configurado para consumo manual de offset.
     */
    public static <T> KafkaConsumer<String, String> createConsumer(String bootstrapServers, String groupId) {
        return createConsumer(bootstrapServers, groupId, null, null, null, null, null);
    }

    /**
     * Cria um {@link KafkaConsumer} com suporte a SASL/SSL para Event Hubs.
     */
    public static <T> KafkaConsumer<String, String> createConsumer(String bootstrapServers, String groupId,
            String securityProtocol, String saslMechanism, String saslJaasConfig) {
        return createConsumer(bootstrapServers, groupId, securityProtocol, saslMechanism, saslJaasConfig, null, null);
    }

    public static <T> KafkaConsumer<String, String> createConsumer(String bootstrapServers, String groupId,
            String securityProtocol, String saslMechanism, String saslJaasConfig,
            String saslUsername, String saslPassword) {
        var props = new HashMap<String, Object>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        applySaslConfig(props, securityProtocol, saslMechanism, saslJaasConfig, saslUsername, saslPassword);
        return new KafkaConsumer<>(props);
    }

    private static void applySaslConfig(Map<String, Object> props, String securityProtocol,
            String saslMechanism, String saslJaasConfig,
            String saslUsername, String saslPassword) {
        if (securityProtocol == null || securityProtocol.isBlank()) {
            return;
        }
        props.put("security.protocol", securityProtocol);
        if (saslMechanism != null && !saslMechanism.isBlank()) {
            props.put("sasl.mechanism", saslMechanism);
        }
        var resolvedJaasConfig = resolveJaasConfig(saslJaasConfig, saslUsername, saslPassword);
        if (resolvedJaasConfig != null && !resolvedJaasConfig.isBlank()) {
            props.put("sasl.jaas.config", resolvedJaasConfig);
            LOGGER.info("Kafka SASL/SSL configurado: protocol={}, mechanism={}", securityProtocol, saslMechanism);
        }
    }

    /**
     * Resolve o JAAS config a partir de KAFKA_SASL_JAAS_CONFIG ou, se este estiver vazio
     * ou claramente corrompido (sem valor para password), constrói a partir de
     * KAFKA_SASL_USERNAME e KAFKA_SASL_PASSWORD separadamente.
     * Isto evita problemas de escaping de aspas em Azure Container Apps.
     */
    public static String resolveJaasConfigPublic(String saslJaasConfig, String saslUsername, String saslPassword) {
        return resolveJaasConfig(saslJaasConfig, saslUsername, saslPassword);
    }

    private static String resolveJaasConfig(String saslJaasConfig, String saslUsername, String saslPassword) {
        LOGGER.debug("resolveJaasConfig: jaasConfig presente={}, username presente={}, password presente={}",
                saslJaasConfig != null && !saslJaasConfig.isBlank(),
                saslUsername != null && !saslUsername.isBlank(),
                saslPassword != null && !saslPassword.isBlank());
        if (saslJaasConfig != null && !saslJaasConfig.isBlank()
                && !saslJaasConfig.contains("******")
                && !saslJaasConfig.matches(".*password\\s*=\\s*[\"']?\\s*[;,].*")) {
            return saslJaasConfig;
        }
        if (saslUsername != null && !saslUsername.isBlank()
                && saslPassword != null && !saslPassword.isBlank()
                && !"******".equals(saslPassword)) {
            LOGGER.info("Construindo JAAS config a partir de KAFKA_SASL_USERNAME/PASSWORD separados");
            return "org.apache.kafka.common.security.plain.PlainLoginModule required username=\""
                    + saslUsername + "\" password=\"" + saslPassword + "\";";
        }
        LOGGER.warn("JAAS config nao pode ser resolvido: saslJaasConfig={}, saslUsername={}",
                saslJaasConfig == null ? "null" : (saslJaasConfig.isBlank() ? "vazio" : "presente"),
                saslUsername == null ? "null" : (saslUsername.isBlank() ? "vazio" : "presente"));
        return saslJaasConfig;
    }
}
class KafkaMessageConsumer implements MessageConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaMessageConsumer.class);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);

    private final String bootstrapServers;
    private final String securityProtocol;
    private final String saslMechanism;
    private final String saslJaasConfig;
    private final String saslUsername;
    private final String saslPassword;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread consumerThread;
    private KafkaConsumer<String, String> consumer;

    KafkaMessageConsumer(String bootstrapServers) {
        this(bootstrapServers, null, null, null);
    }

    KafkaMessageConsumer(String bootstrapServers, String securityProtocol,
                          String saslMechanism, String saslJaasConfig) {
        this(bootstrapServers, securityProtocol, saslMechanism, saslJaasConfig, null, null);
    }

    KafkaMessageConsumer(String bootstrapServers, String securityProtocol,
                          String saslMechanism, String saslJaasConfig,
                          String saslUsername, String saslPassword) {
        this.bootstrapServers = bootstrapServers;
        this.securityProtocol = securityProtocol;
        this.saslMechanism = saslMechanism;
        this.saslJaasConfig = saslJaasConfig;
        this.saslUsername = saslUsername;
        this.saslPassword = saslPassword;
    }

    @Override
    public <T> void start(String topic, String route, Class<T> payloadType, MessageHandler<T> handler) {
        if (running.compareAndSet(false, true)) {
            consumer = KafkaMessagePublisher.createConsumer(bootstrapServers, route, securityProtocol, saslMechanism, saslJaasConfig, saslUsername, saslPassword);
            consumer.subscribe(java.util.List.of(topic));
            consumerThread = new Thread(() -> {
                while (running.get()) {
                    try {
                        var records = consumer.poll(POLL_TIMEOUT);
                        for (var record : records) {
                            try {
                                var payload = JsonDefaults.objectMapper().readValue(record.value(), payloadType);
                                handler.handle(payload);
                                consumer.commitSync();
                            } catch (Exception e) {
                                LOGGER.error("Falha ao processar mensagem Kafka topic={} offset={}", topic, record.offset(), e);
                            }
                        }
                    } catch (Exception e) {
                        if (running.get()) {
                            LOGGER.warn("Erro no poll Kafka topic={}", topic, e);
                        }
                    }
                }
            }, "kafka-consumer-" + topic);
            consumerThread.setDaemon(true);
            consumerThread.start();
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (consumer != null) {
            consumer.wakeup();
            consumer.close();
        }
    }

    @Override
    public void checkpoint() {
        if (consumer != null) {
            consumer.commitSync();
        }
    }
}
