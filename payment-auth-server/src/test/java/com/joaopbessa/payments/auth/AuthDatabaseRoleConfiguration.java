package com.joaopbessa.payments.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the application pool as the production database role ({@code payment-auth-app}, read-only on the user
 * tables), while Flyway and the test fixtures keep the container administrator.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AuthDatabaseRoleConfiguration {

	public static final String AUTH_ROLE = "payment-auth-app";

	private static final String SCHEMA = "payment";
	private static final String SET_ROLE = "SET ROLE \"%s\"";
	private static final String CONNECTION_INIT_SQL = "spring.datasource.hikari.connection-init-sql";
	private static final String FLYWAY_USER = "spring.flyway.user";
	private static final String FLYWAY_PASSWORD = "spring.flyway.password";

	@Bean
	DynamicPropertyRegistrar authRoleRegistrar(PostgreSQLContainer postgres) {
		return registry -> {
			registry.add(CONNECTION_INIT_SQL, () -> SET_ROLE.formatted(AUTH_ROLE));
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
