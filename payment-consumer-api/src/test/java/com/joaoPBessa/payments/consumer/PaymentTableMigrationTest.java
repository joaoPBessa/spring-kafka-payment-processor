package com.joaoPBessa.payments.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
@DisplayName("V3 payment table migration")
class PaymentTableMigrationTest {

    private static final String INSERT_ACCOUNT = "INSERT INTO account (id, number, name) VALUES (?, ?, ?)";
    private static final String INSERT_PAYMENT = """
            INSERT INTO payment (id, transaction_id, source_account, target_account, amount, currency,
                                 payment_method, event_timestamp)
            VALUES (?, ?, ?, ?, ?, ?, 'PIX', ?)
            """;
    private static final String SELECT_CREATED_AT = "SELECT created_at FROM payment WHERE transaction_id = ?";

    private static final String SOURCE_ACCOUNT = "100001";
    private static final String TARGET_ACCOUNT = "100002";
    private static final String UNKNOWN_ACCOUNT = "999999";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("150.75");
    private static final String VALID_CURRENCY = "USD";

    private record PaymentRow(String transactionId, String sourceAccount, String targetAccount,
                              BigDecimal amount, String currency) {

        static PaymentRow valid(String transactionId) {
            return new PaymentRow(transactionId, SOURCE_ACCOUNT, TARGET_ACCOUNT, VALID_AMOUNT, VALID_CURRENCY);
        }

        PaymentRow withSourceAccount(String account) {
            return new PaymentRow(transactionId, account, targetAccount, amount, currency);
        }

        PaymentRow withTargetAccount(String account) {
            return new PaymentRow(transactionId, sourceAccount, account, amount, currency);
        }

        PaymentRow withAmount(BigDecimal newAmount) {
            return new PaymentRow(transactionId, sourceAccount, targetAccount, newAmount, currency);
        }

        PaymentRow withCurrency(String newCurrency) {
            return new PaymentRow(transactionId, sourceAccount, targetAccount, amount, newCurrency);
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void insertAccount(String number) {
        jdbcTemplate.update(INSERT_ACCOUNT, UUID.randomUUID(), number, "Account " + number);
    }

    private void insertPayment(PaymentRow row) {
        jdbcTemplate.update(INSERT_PAYMENT, UUID.randomUUID(), row.transactionId(), row.sourceAccount(),
                row.targetAccount(), row.amount(), row.currency(), Timestamp.from(Instant.now()));
    }

    private void givenSourceAndTargetAccounts() {
        insertAccount(SOURCE_ACCOUNT);
        insertAccount(TARGET_ACCOUNT);
    }

    @Test
    @DisplayName("Should accept a valid payment and fill created_at from the database default")
    void shouldAcceptValidPaymentAndFillCreatedAt() {
        givenSourceAndTargetAccounts();
        String transactionId = UUID.randomUUID().toString();

        insertPayment(PaymentRow.valid(transactionId));

        assertThat(jdbcTemplate.queryForObject(SELECT_CREATED_AT, Timestamp.class, transactionId)).isNotNull();
    }

    @Test
    @DisplayName("Should reject a second payment with the same transaction id")
    void shouldRejectDuplicatedTransactionId() {
        givenSourceAndTargetAccounts();
        String transactionId = UUID.randomUUID().toString();
        insertPayment(PaymentRow.valid(transactionId));

        assertThatThrownBy(() -> insertPayment(PaymentRow.valid(transactionId)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_payment_transaction_id");
    }

    @Test
    @DisplayName("Should reject a payment whose source account does not exist")
    void shouldRejectUnknownSourceAccount() {
        givenSourceAndTargetAccounts();
        PaymentRow row = PaymentRow.valid(UUID.randomUUID().toString()).withSourceAccount(UNKNOWN_ACCOUNT);

        assertThatThrownBy(() -> insertPayment(row))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_payment_source_account");
    }

    @Test
    @DisplayName("Should reject a payment whose target account does not exist")
    void shouldRejectUnknownTargetAccount() {
        givenSourceAndTargetAccounts();
        PaymentRow row = PaymentRow.valid(UUID.randomUUID().toString()).withTargetAccount(UNKNOWN_ACCOUNT);

        assertThatThrownBy(() -> insertPayment(row))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_payment_target_account");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00"})
    @DisplayName("Should reject a payment whose amount is not positive")
    void shouldRejectNonPositiveAmount(String amount) {
        givenSourceAndTargetAccounts();
        PaymentRow row = PaymentRow.valid(UUID.randomUUID().toString()).withAmount(new BigDecimal(amount));

        assertThatThrownBy(() -> insertPayment(row))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payment_amount_positive");
    }

    @Test
    @DisplayName("Should reject a currency longer than three characters")
    void shouldRejectCurrencyLongerThanThreeCharacters() {
        givenSourceAndTargetAccounts();
        PaymentRow row = PaymentRow.valid(UUID.randomUUID().toString()).withCurrency("USDX");

        assertThatThrownBy(() -> insertPayment(row)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should reject a transaction id longer than 36 characters")
    void shouldRejectTransactionIdLongerThanThirtySixCharacters() {
        givenSourceAndTargetAccounts();
        PaymentRow row = PaymentRow.valid("x".repeat(37));

        assertThatThrownBy(() -> insertPayment(row)).isInstanceOf(DataIntegrityViolationException.class);
    }

}
