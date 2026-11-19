package com.joaopbessa.payments.auth.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.joaopbessa.payments.auth.PostgresContainerConfiguration;
import com.joaopbessa.payments.common.security.PasswordEncoderAutoConfiguration;
import jakarta.persistence.EntityManagerFactory;
import com.joaopbessa.payments.auth.domain.entities.AppUser;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
@ImportAutoConfiguration(PasswordEncoderAutoConfiguration.class)
@DisplayName("AppUserRepository")
class AppUserRepositoryTest {

    private static final String INSERT_USER = "INSERT INTO app_user (id, username, password, enabled) VALUES (?, ?, ?, ?)";
    private static final String INSERT_ROLE = "INSERT INTO app_user_role (user_id, role) VALUES (?, ?)";
    private static final String STORED_PASSWORD = "{bcrypt}" + new BCryptPasswordEncoder().encode("secret");

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
    @DisplayName("Should load a user together with its roles in a single query")
    void shouldLoadUserAndRolesInASingleQuery() {
        givenUser("single-query-user", true, "ACCOUNT_READ", "ACCOUNT_WRITE", "PAYMENT_CREATE");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        AppUser user = appUserRepository.findByUsername("single-query-user").orElseThrow();

        assertThat(user.getRoles()).hasSize(3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
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

        assertThat(operator.getRoles()).containsExactlyInAnyOrder("DLT_VIEW", "DLT_REPLAY");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "producer-client", "producer-reader", "dlt-operator", "dlt-viewer" })
    @DisplayName("Should store the password of a demonstration user as a bcrypt hash that matches its documented password")
    void shouldStoreDemonstrationPasswordsAsBcryptHashesThatMatchTheDocumentedOnes(String username) {
        AppUser user = appUserRepository.findByUsername(username).orElseThrow();

        assertThat(user.getPassword()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(username + "-demo", user.getPassword())).isTrue();
    }

}
