package com.joaoPBessa.payments.consumer.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.joaoPBessa.payments.consumer.PostgresContainerConfiguration;
import com.joaoPBessa.payments.consumer.domain.entities.Payment;
import com.joaopBessa.payments.common.domain.PaymentMethod;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
@DisplayName("PaymentRepository")
class PaymentRepositoryTest {

    private static final String INSERT_ACCOUNT = "INSERT INTO account (id, number, name) VALUES (?, ?, ?)";
    private static final String SOURCE_ACCOUNT = "200001";
    private static final String TARGET_ACCOUNT = "200002";
    private static final Instant EVENT_TIMESTAMP = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Payment paymentWith(String transactionId) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .sourceAccount(SOURCE_ACCOUNT)
                .targetAccount(TARGET_ACCOUNT)
                .amount(new BigDecimal("150.75"))
                .currency("USD")
                .paymentMethod(PaymentMethod.PIX)
                .eventTimestamp(EVENT_TIMESTAMP)
                .build();
    }

    private void givenSourceAndTargetAccounts() {
        jdbcTemplate.update(INSERT_ACCOUNT, UUID.randomUUID(), SOURCE_ACCOUNT, "Source");
        jdbcTemplate.update(INSERT_ACCOUNT, UUID.randomUUID(), TARGET_ACCOUNT, "Target");
    }

    @Test
    @DisplayName("Should persist every field and let the database fill created_at")
    void shouldPersistEveryFieldAndFillCreatedAt() {
        givenSourceAndTargetAccounts();
        Payment payment = paymentRepository.saveAndFlush(paymentWith(UUID.randomUUID().toString()));
        entityManager.clear();

        Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(reloaded.getTransactionId()).isEqualTo(payment.getTransactionId());
        assertThat(reloaded.getAmount()).isEqualTo(new BigDecimal("150.75"));
        assertThat(reloaded.getPaymentMethod()).isEqualTo(PaymentMethod.PIX);
        assertThat(reloaded.getEventTimestamp()).isEqualTo(EVENT_TIMESTAMP);
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should report an existing transaction id")
    void shouldReportExistingTransactionId() {
        givenSourceAndTargetAccounts();
        String transactionId = UUID.randomUUID().toString();
        paymentRepository.saveAndFlush(paymentWith(transactionId));

        assertThat(paymentRepository.existsByTransactionId(transactionId)).isTrue();
    }

    @Test
    @DisplayName("Should report a missing transaction id")
    void shouldReportMissingTransactionId() {
        assertThat(paymentRepository.existsByTransactionId(UUID.randomUUID().toString())).isFalse();
    }

}
