package com.joaoPBessa.payments.consumer;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.joaoPBessa.payments.consumer.config.KafkaConsumerProperties;

@TestConfiguration(proxyBeanMethods = false)
public class KafkaInfrastructureConfiguration {

	private static final String KAFKA_IMAGE = "apache/kafka:4.1.1";
	private static final String APICURIO_IMAGE = "apicurio/apicurio-registry:3.3.2";
	private static final int APICURIO_PORT = 8080;
	private static final String APICURIO_HEALTH_PATH = "/apis/registry/v3/system/info";
	private static final String APICURIO_API_URL = "http://%s:%d/apis/registry/v3";
	private static final String CONSUMER_REGISTRY_URL = "spring.kafka.consumer.properties.apicurio.registry.url";
	private static final String PRODUCER_REGISTRY_URL = "spring.kafka.producer.properties.apicurio.registry.url";

	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE));
	}

	@Bean
	GenericContainer<?> apicurioContainer() {
		return new GenericContainer<>(DockerImageName.parse(APICURIO_IMAGE))
				.withExposedPorts(APICURIO_PORT)
				.waitingFor(Wait.forHttp(APICURIO_HEALTH_PATH).forPort(APICURIO_PORT).forStatusCode(200));
	}

	@Bean
	DynamicPropertyRegistrar apicurioRegistryUrlRegistrar(GenericContainer<?> apicurioContainer) {
		return registry -> {
			registry.add(CONSUMER_REGISTRY_URL, () -> apicurioUrl(apicurioContainer));
			registry.add(PRODUCER_REGISTRY_URL, () -> apicurioUrl(apicurioContainer));
		};
	}

	@Bean
	NewTopic paymentEventsTopic(KafkaConsumerProperties properties) {
		return TopicBuilder.name(properties.topics().paymentEvents()).partitions(1).replicas(1).build();
	}

	private String apicurioUrl(GenericContainer<?> apicurioContainer) {
		return APICURIO_API_URL.formatted(apicurioContainer.getHost(), apicurioContainer.getMappedPort(APICURIO_PORT));
	}

}
