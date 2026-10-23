package com.joaoPBessa.payments.consumer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfiguration {

	private static final String POSTGRES_IMAGE = "postgres:15.18-alpine";
	private static final String DATABASE_NAME = "payments_db";
	private static final String INIT_SCRIPT = "init-roles.sql";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
				.withDatabaseName(DATABASE_NAME)
				.withInitScript(INIT_SCRIPT);
	}

}
