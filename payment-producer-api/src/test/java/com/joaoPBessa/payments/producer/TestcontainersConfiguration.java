package com.joaoPBessa.payments.producer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	private static final int APICURIO_PORT = 8080;
	private static final String APICURIO_HEALTH_PATH = "/apis/registry/v3/system/info";
	private static final String APICURIO_API_URL = "http://%s:%d/apis/registry/v3";
	private static final String PRODUCER_REGISTRY_URL = "spring.kafka.producer.properties.apicurio.registry.url";

	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.1"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7.2.15-alpine")).withExposedPorts(6379);
	}

	@Bean
	GenericContainer<?> apicurioContainer() {
		return new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry:3.3.2"))
				.withExposedPorts(APICURIO_PORT)
				.waitingFor(Wait.forHttp(APICURIO_HEALTH_PATH).forPort(APICURIO_PORT).forStatusCode(200));
	}

	@Bean
	DynamicPropertyRegistrar apicurioRegistryUrlRegistrar(GenericContainer<?> apicurioContainer) {
		return registry -> registry.add(PRODUCER_REGISTRY_URL,
				() -> APICURIO_API_URL.formatted(apicurioContainer.getHost(), apicurioContainer.getMappedPort(APICURIO_PORT)));
	}

}
