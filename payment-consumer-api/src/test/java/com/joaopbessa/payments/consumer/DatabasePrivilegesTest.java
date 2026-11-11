package com.joaopbessa.payments.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Database privileges of the application roles")
class DatabasePrivilegesTest {

    private static final String CONSUMER_ROLE = "payment-consumer-app";
    private static final String CONSUMER_PASSWORD = "Zx7mP4rQd9LkV2nT";
    private static final String PRODUCER_ROLE = "payment-producer-app";
    private static final String PRODUCER_PASSWORD = "K1SJdZqbkJyW^qM)";

    private static final String PAYMENT_TABLE = "payment.payment";
    private static final String ACCOUNT_TABLE = "payment.account";
    private static final String HAS_TABLE_PRIVILEGE = "SELECT has_table_privilege(?, ?, ?)";
    private static final String HAS_SCHEMA_PRIVILEGE = "SELECT has_schema_privilege(?, 'payment', ?)";
    private static final String ROLE_FLAGS = """
            SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls, rolcanlogin
            FROM pg_roles WHERE rolname = ?
            """;
    private static final String PUBLIC_GRANTS =
            "SELECT count(*) FROM information_schema.role_table_grants WHERE grantee = 'PUBLIC' AND table_schema = 'payment'";

    private static final String INSERT_ACCOUNT = "INSERT INTO account (id, number, name) VALUES (?, ?, ?)";
    private static final String INSERT_PAYMENT = """
            INSERT INTO payment (id, transaction_id, source_account, target_account, amount, currency,
                                 payment_method, event_timestamp)
            VALUES (?, ?, ?, ?, ?, 'USD', 'PIX', ?)
            """;
    private static final String SELECT_ACCOUNTS = "SELECT count(*) FROM account";
    private static final String SELECT_PAYMENTS = "SELECT count(*) FROM payment";
    private static final String UPDATE_ACCOUNT = "UPDATE account SET name = ? WHERE number = ?";
    private static final String DELETE_ACCOUNT = "DELETE FROM account WHERE number = ?";
    private static final String DELETE_PAYMENTS = "DELETE FROM payment WHERE transaction_id = ?";

    private static final String PERMISSION_DENIED = "42501";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String UNIQUE_VIOLATION = "23505";

    private static final String SOURCE_ACCOUNT = "810001";
    private static final String TARGET_ACCOUNT = "810002";
    private static final String UNKNOWN_ACCOUNT = "819999";

    @Autowired
    private PostgreSQLContainer postgres;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM payment WHERE source_account IN (?, ?)", SOURCE_ACCOUNT, TARGET_ACCOUNT);
        jdbcTemplate.update("DELETE FROM account WHERE number IN (?, ?)", SOURCE_ACCOUNT, TARGET_ACCOUNT);
    }

    private boolean hasTablePrivilege(String role, String table, String privilege) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(HAS_TABLE_PRIVILEGE, Boolean.class, role, table, privilege));
    }

    private boolean hasSchemaPrivilege(String role, String privilege) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(HAS_SCHEMA_PRIVILEGE, Boolean.class, role, privilege));
    }

    private Connection loginAs(String role, String password) throws SQLException {
        Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), role, password);
        connection.setAutoCommit(true);
        connection.createStatement().execute("SET search_path TO payment");
        return connection;
    }

    private void givenSourceAndTargetAccounts() {
        jdbcTemplate.update(INSERT_ACCOUNT, UUID.randomUUID(), SOURCE_ACCOUNT, "Source");
        jdbcTemplate.update(INSERT_ACCOUNT, UUID.randomUUID(), TARGET_ACCOUNT, "Target");
    }

    private void insertPayment(Connection connection, String transactionId, String targetAccount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_PAYMENT)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, transactionId);
            statement.setString(3, SOURCE_ACCOUNT);
            statement.setString(4, targetAccount);
            statement.setBigDecimal(5, new BigDecimal("10.00"));
            statement.setTimestamp(6, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private void execute(Connection connection, String sql, String... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            statement.execute();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "SELECT", "INSERT" })
    @DisplayName("Should let the consumer read and insert payments")
    void shouldLetConsumerReadAndInsertPayments(String privilege) {
        assertThat(hasTablePrivilege(CONSUMER_ROLE, PAYMENT_TABLE, privilege)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER" })
    @DisplayName("Should keep the consumer from changing or restructuring payments")
    void shouldKeepConsumerFromChangingOrRestructuringPayments(String privilege) {
        assertThat(hasTablePrivilege(CONSUMER_ROLE, PAYMENT_TABLE, privilege)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER" })
    @DisplayName("Should keep the consumer away from accounts")
    void shouldKeepConsumerAwayFromAccounts(String privilege) {
        assertThat(hasTablePrivilege(CONSUMER_ROLE, ACCOUNT_TABLE, privilege)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "SELECT", "INSERT", "UPDATE", "DELETE" })
    @DisplayName("Should let the producer manage accounts")
    void shouldLetProducerManageAccounts(String privilege) {
        assertThat(hasTablePrivilege(PRODUCER_ROLE, ACCOUNT_TABLE, privilege)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER" })
    @DisplayName("Should keep the producer away from payments")
    void shouldKeepProducerAwayFromPayments(String privilege) {
        assertThat(hasTablePrivilege(PRODUCER_ROLE, PAYMENT_TABLE, privilege)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { CONSUMER_ROLE, PRODUCER_ROLE })
    @DisplayName("Should use the schema but never create objects in it")
    void shouldUseTheSchemaButNeverCreateObjectsInIt(String role) {
        assertThat(hasSchemaPrivilege(role, "USAGE")).isTrue();
        assertThat(hasSchemaPrivilege(role, "CREATE")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { CONSUMER_ROLE, PRODUCER_ROLE })
    @DisplayName("Should be plain login roles without elevated attributes")
    void shouldBePlainLoginRolesWithoutElevatedAttributes(String role) {
        List<Boolean> flags = jdbcTemplate.query(ROLE_FLAGS, (resultSet, row) -> resultSet.getBoolean(1), role);
        List<Boolean> canLogin = jdbcTemplate.query(ROLE_FLAGS, (resultSet, row) -> resultSet.getBoolean(2), role);

        assertThat(flags).containsExactly(false);
        assertThat(canLogin).containsExactly(true);
    }

    @Test
    @DisplayName("Should grant nothing on the payment tables to PUBLIC")
    void shouldGrantNothingOnThePaymentTablesToPublic() {
        assertThat(jdbcTemplate.queryForObject(PUBLIC_GRANTS, Integer.class)).isZero();
    }

    @Test
    @DisplayName("Should let the consumer log in and insert a payment")
    void shouldLetConsumerLogInAndInsertPayment() throws SQLException {
        givenSourceAndTargetAccounts();

        try (Connection consumer = loginAs(CONSUMER_ROLE, CONSUMER_PASSWORD)) {
            insertPayment(consumer, UUID.randomUUID().toString(), TARGET_ACCOUNT);
            execute(consumer, SELECT_PAYMENTS);
        }
    }

    @Test
    @DisplayName("Should report an unknown account as a foreign key violation, not as a permission error")
    void shouldReportUnknownAccountAsForeignKeyViolation() throws SQLException {
        givenSourceAndTargetAccounts();

        try (Connection consumer = loginAs(CONSUMER_ROLE, CONSUMER_PASSWORD)) {
            assertThatThrownBy(() -> insertPayment(consumer, UUID.randomUUID().toString(), UNKNOWN_ACCOUNT))
                    .isInstanceOfSatisfying(SQLException.class,
                            failure -> assertThat(failure.getSQLState()).isEqualTo(FOREIGN_KEY_VIOLATION));
        }
    }

    @Test
    @DisplayName("Should report a repeated transaction id as a unique violation")
    void shouldReportRepeatedTransactionIdAsUniqueViolation() throws SQLException {
        givenSourceAndTargetAccounts();
        String transactionId = UUID.randomUUID().toString();

        try (Connection consumer = loginAs(CONSUMER_ROLE, CONSUMER_PASSWORD)) {
            insertPayment(consumer, transactionId, TARGET_ACCOUNT);

            assertThatThrownBy(() -> insertPayment(consumer, transactionId, TARGET_ACCOUNT))
                    .isInstanceOfSatisfying(SQLException.class,
                            failure -> assertThat(failure.getSQLState()).isEqualTo(UNIQUE_VIOLATION));
        }
    }

    @Test
    @DisplayName("Should refuse the consumer a read of accounts")
    void shouldRefuseConsumerAReadOfAccounts() throws SQLException {
        try (Connection consumer = loginAs(CONSUMER_ROLE, CONSUMER_PASSWORD)) {
            assertThatThrownBy(() -> execute(consumer, SELECT_ACCOUNTS))
                    .isInstanceOfSatisfying(SQLException.class,
                            failure -> assertThat(failure.getSQLState()).isEqualTo(PERMISSION_DENIED));
        }
    }

    @Test
    @DisplayName("Should refuse the producer a read of payments")
    void shouldRefuseProducerAReadOfPayments() throws SQLException {
        try (Connection producer = loginAs(PRODUCER_ROLE, PRODUCER_PASSWORD)) {
            assertThatThrownBy(() -> execute(producer, SELECT_PAYMENTS))
                    .isInstanceOfSatisfying(SQLException.class,
                            failure -> assertThat(failure.getSQLState()).isEqualTo(PERMISSION_DENIED));
        }
    }

    @Test
    @DisplayName("Should let the producer log in and manage accounts")
    void shouldLetProducerLogInAndManageAccounts() throws SQLException {
        try (Connection producer = loginAs(PRODUCER_ROLE, PRODUCER_PASSWORD)) {
            try (PreparedStatement insert = producer.prepareStatement(INSERT_ACCOUNT)) {
                insert.setObject(1, UUID.randomUUID());
                insert.setString(2, SOURCE_ACCOUNT);
                insert.setString(3, "Producer account");
                insert.executeUpdate();
            }
            execute(producer, UPDATE_ACCOUNT, "Renamed", SOURCE_ACCOUNT);
            execute(producer, DELETE_ACCOUNT, SOURCE_ACCOUNT);
        }
    }

    @Test
    @DisplayName("Should refuse the consumer a delete of payments")
    void shouldRefuseConsumerADeleteOfPayments() throws SQLException {
        try (Connection consumer = loginAs(CONSUMER_ROLE, CONSUMER_PASSWORD)) {
            assertThatThrownBy(() -> execute(consumer, DELETE_PAYMENTS, "none"))
                    .isInstanceOfSatisfying(SQLException.class,
                            failure -> assertThat(failure.getSQLState()).isEqualTo(PERMISSION_DENIED));
        }
    }

}
