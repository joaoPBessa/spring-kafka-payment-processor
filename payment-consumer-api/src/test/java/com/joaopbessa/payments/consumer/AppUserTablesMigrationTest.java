package com.joaopbessa.payments.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.stream.Stream;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
@DisplayName("V5 and V6 application user migrations")
class AppUserTablesMigrationTest {

    private static final String INSERT_USER = "INSERT INTO app_user (id, username, password) VALUES (?, ?, ?)";
    private static final String INSERT_ROLE = "INSERT INTO app_user_role (user_id, role) VALUES (?, ?)";
    private static final String SELECT_ENABLED = "SELECT enabled FROM app_user WHERE username = ?";
    private static final String SELECT_CREATED_AT = "SELECT created_at FROM app_user WHERE username = ?";
    private static final String SELECT_PASSWORD = "SELECT password FROM app_user WHERE username = ?";
    private static final String SELECT_ROLES = """
            SELECT r.role FROM app_user_role r JOIN app_user u ON u.id = r.user_id
            WHERE u.username = ? ORDER BY r.role
            """;
    private static final String COUNT_ROLES_OF_USER = "SELECT count(*) FROM app_user_role WHERE user_id = ?";

    private static final String ENCODED_PASSWORD = "{base64}ZGVtbw==";
    private static final String BASE64_ID_PREFIX = "{base64}";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID insertUser(String username) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(INSERT_USER, id, username, ENCODED_PASSWORD);
        return id;
    }

    static Stream<Arguments> demoUsers() {
        return Stream.of(
                Arguments.of("producer-client", List.of("ACCOUNT_READ", "ACCOUNT_WRITE", "PAYMENT_CREATE")),
                Arguments.of("producer-reader", List.of("ACCOUNT_READ")),
                Arguments.of("dlt-operator", List.of("DLT_REPLAY", "DLT_VIEW")),
                Arguments.of("dlt-viewer", List.of("DLT_VIEW")));
    }

    private static String encodedDemoPassword(String username) {
        String rawPassword = username + "-demo";
        return BASE64_ID_PREFIX + Base64.getEncoder().encodeToString(rawPassword.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Should enable a new user by default and fill created_at from the database default")
    void shouldEnableANewUserByDefaultAndFillCreatedAt() {
        String username = "new-user";
        insertUser(username);

        assertThat(jdbcTemplate.queryForObject(SELECT_ENABLED, Boolean.class, username)).isTrue();
        assertThat(jdbcTemplate.queryForObject(SELECT_CREATED_AT, Timestamp.class, username)).isNotNull();
    }

    @Test
    @DisplayName("Should reject a second user with the same username")
    void shouldRejectDuplicatedUsername() {
        insertUser("duplicated-user");

        assertThatThrownBy(() -> insertUser("duplicated-user"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_app_user_username");
    }

    @Test
    @DisplayName("Should reject a role for a user that does not exist")
    void shouldRejectARoleForAnUnknownUser() {
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_ROLE, UUID.randomUUID(), "ACCOUNT_READ"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_app_user_role_user");
    }

    @Test
    @DisplayName("Should reject the same role twice for the same user")
    void shouldRejectTheSameRoleTwiceForTheSameUser() {
        UUID userId = insertUser("role-user");
        jdbcTemplate.update(INSERT_ROLE, userId, "ACCOUNT_READ");

        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_ROLE, userId, "ACCOUNT_READ"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("pk_app_user_role");
    }

    @Test
    @DisplayName("Should remove the roles of a user when the user is deleted")
    void shouldRemoveTheRolesOfAUserWhenTheUserIsDeleted() {
        UUID userId = insertUser("deleted-user");
        jdbcTemplate.update(INSERT_ROLE, userId, "ACCOUNT_READ");

        jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", userId);

        assertThat(jdbcTemplate.queryForObject(COUNT_ROLES_OF_USER, Integer.class, userId)).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoUsers")
    @DisplayName("Should seed the demo user with its roles and an encoded password")
    void shouldSeedTheDemoUserWithItsRolesAndAnEncodedPassword(String username, List<String> roles) {
        assertThat(jdbcTemplate.queryForObject(SELECT_ENABLED, Boolean.class, username)).isTrue();
        assertThat(jdbcTemplate.queryForObject(SELECT_PASSWORD, String.class, username))
                .isEqualTo(encodedDemoPassword(username));
        assertThat(jdbcTemplate.queryForList(SELECT_ROLES, String.class, username)).containsExactlyElementsOf(roles);
    }

}
