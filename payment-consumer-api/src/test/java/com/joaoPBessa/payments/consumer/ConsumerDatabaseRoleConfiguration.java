package com.joaoPBessa.payments.consumer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class ConsumerDatabaseRoleConfiguration {

	public static final String CONSUMER_ROLE = "payment-consumer-app";

	private static final String SCHEMA = "payment";
	private static final String SET_ROLE = "SET ROLE \"%s\"";
	private static final String CONNECTION_INIT_SQL = "spring.datasource.hikari.connection-init-sql";
	private static final String FLYWAY_USER = "spring.flyway.user";
	private static final String FLYWAY_PASSWORD = "spring.flyway.password";

	@Bean
	DynamicPropertyRegistrar consumerRoleRegistrar(PostgreSQLContainer postgres) {
		return registry -> {
			registry.add(CONNECTION_INIT_SQL, () -> SET_ROLE.formatted(CONSUMER_ROLE));
			registry.add(FLYWAY_USER, postgres::getUsername);
			registry.add(FLYWAY_PASSWORD, postgres::getPassword);
		};
	}

	@Bean
	JdbcTemplate adminJdbcTemplate(PostgreSQLContainer postgres) {
		DriverManagerDataSource adminDataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		adminDataSource.setSchema(SCHEMA);
		return new JdbcTemplate(adminDataSource);
	}

}
