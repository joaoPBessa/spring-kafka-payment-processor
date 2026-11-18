package com.joaopbessa.payments.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * The database credentials and the OAuth2 client secrets live in Vault ({@code secret/payment-auth-server}); no
 * configuration file shipped with the module may carry them, not even as a development default.
 */
@DisplayName("Credentials in the configuration files of the authorization server")
class NoCredentialsInConfigTest {

    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");
    private static final Set<String> CREDENTIAL_NAMES = Set.of(
            "username", "user", "password", "secret", "client-secret", "clientsecret", "api-key", "apikey");
    private static final String DATASOURCE_URL = "spring.datasource.url";

    private List<PropertySource<?>> loadMainConfigurationFiles() throws IOException {
        List<PropertySource<?>> sources = new ArrayList<>();
        try (Stream<Path> files = Files.list(MAIN_RESOURCES)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                FileSystemResource resource = new FileSystemResource(file);
                if (name.endsWith(".yaml") || name.endsWith(".yml")) {
                    sources.addAll(new YamlPropertySourceLoader().load(name, resource));
                } else if (name.endsWith(".properties")) {
                    sources.addAll(new PropertiesPropertySourceLoader().load(name, resource));
                }
            }
        }
        return sources;
    }

    private List<String> propertyNames(List<PropertySource<?>> sources) {
        return sources.stream()
                .filter(EnumerablePropertySource.class::isInstance)
                .flatMap(source -> Stream.of(((EnumerablePropertySource<?>) source).getPropertyNames()))
                .toList();
    }

    private static String lastSegment(String propertyName) {
        String withoutIndex = propertyName.replaceAll("\\[\\d+]$", "");
        return withoutIndex.substring(withoutIndex.lastIndexOf('.') + 1).toLowerCase();
    }

    @Test
    @DisplayName("Should find the configuration files it is going to check")
    void shouldFindTheConfigurationFiles() throws IOException {
        List<String> names = propertyNames(loadMainConfigurationFiles());

        assertThat(names).contains(DATASOURCE_URL, "spring.cloud.vault.application-name");
    }

    @Test
    @DisplayName("Should not declare any username, password or secret in an application file")
    void shouldNotDeclareUsernamesPasswordsOrSecrets() throws IOException {
        List<String> offending = propertyNames(loadMainConfigurationFiles()).stream()
                .filter(name -> CREDENTIAL_NAMES.contains(lastSegment(name)))
                .toList();

        assertThat(offending).isEmpty();
    }

    @Test
    @DisplayName("Should only point the datasource at the database, leaving the credentials to Vault")
    void shouldOnlyPointTheDatasourceAtTheDatabase() throws IOException {
        List<String> datasourceSettings = propertyNames(loadMainConfigurationFiles()).stream()
                .filter(name -> name.startsWith("spring.datasource.") && !name.startsWith("spring.datasource.hikari."))
                .toList();

        assertThat(datasourceSettings).containsExactly(DATASOURCE_URL);
    }

}
