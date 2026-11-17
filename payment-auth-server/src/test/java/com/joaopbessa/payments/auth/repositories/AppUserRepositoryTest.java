package com.joaopbessa.payments.auth.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.joaopbessa.payments.auth.PostgresContainerConfiguration;
import com.joaopbessa.payments.auth.domain.entities.AppUser;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
@DisplayName("AppUserRepository")
class AppUserRepositoryTest {

    private static final String INSERT_USER = "INSERT INTO app_user (id, username, password, enabled) VALUES (?, ?, ?, ?)";
    private static final String INSERT_ROLE = "INSERT INTO app_user_role (user_id, role) VALUES (?, ?)";
    private static final String STORED_PASSWORD = "{base64}c2VjcmV0";

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID givenUser(String username, boolean enabled, String... roles) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(INSERT_USER, id, username, STORED_PASSWORD, enabled);
        for (String role : roles) {
            jdbcTemplate.update(INSERT_ROLE, id, role);
        }
        return id;
    }

    @Test
    @DisplayName("Should find a user by username together with the stored password and every role")
    void shouldFindUserWithPasswordAndRoles() {
        UUID id = givenUser("repository-user", true, "ACCOUNT_READ", "ACCOUNT_WRITE");

        AppUser user = appUserRepository.findByUsername("repository-user").orElseThrow();

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUsername()).isEqualTo("repository-user");
        assertThat(user.getPassword()).isEqualTo(STORED_PASSWORD);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getRoles()).containsExactlyInAnyOrder("ACCOUNT_READ", "ACCOUNT_WRITE");
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should load a user that has no roles with an empty role set")
    void shouldLoadUserWithoutRoles() {
        givenUser("user-without-roles", true);

        AppUser user = appUserRepository.findByUsername("user-without-roles").orElseThrow();

        assertThat(user.getRoles()).isEmpty();
    }

    @Test
    @DisplayName("Should expose a disabled user so the login can reject it")
    void shouldExposeDisabledUser() {
        givenUser("disabled-user", false, "DLT_VIEW");

        AppUser user = appUserRepository.findByUsername("disabled-user").orElseThrow();

        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should return nothing for an unknown username")
    void shouldReturnNothingForUnknownUsername() {
        assertThat(appUserRepository.findByUsername("nobody")).isEmpty();
    }

    @Test
    @DisplayName("Should find the users seeded by the migrations with the roles of each demonstration profile")
    void shouldFindSeededDemonstrationUsers() {
        AppUser operator = appUserRepository.findByUsername("dlt-operator").orElseThrow();

        assertThat(operator.getPassword()).startsWith("{base64}");
        assertThat(operator.getRoles()).containsExactlyInAnyOrder("DLT_VIEW", "DLT_REPLAY");
    }

}
