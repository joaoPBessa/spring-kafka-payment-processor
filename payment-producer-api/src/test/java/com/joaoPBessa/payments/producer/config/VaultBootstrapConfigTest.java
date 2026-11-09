package com.joaoPBessa.payments.producer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

@DisplayName("Vault bootstrap configuration")
class VaultBootstrapConfigTest {

    private static final String BOOTSTRAP_FILE = "bootstrap.yml";
    private static final String VAULT_APPLICATION_NAME = "spring.cloud.vault.application-name";
    private static final String PRODUCER_APPLICATION = "payment-producer-api";

    private Object bootstrapProperty(String name) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(BOOTSTRAP_FILE, new ClassPathResource(BOOTSTRAP_FILE));
        return sources.get(0).getProperty(name);
    }

    @Test
    @DisplayName("Should read its own Vault secret instead of a shared one")
    void shouldReadItsOwnVaultSecretInsteadOfASharedOne() throws IOException {
        assertThat(bootstrapProperty(VAULT_APPLICATION_NAME)).isEqualTo(PRODUCER_APPLICATION);
    }

}
