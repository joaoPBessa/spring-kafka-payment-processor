package com.joaopbessa.payments.producer.repositories.specifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.joaopbessa.payments.producer.api.dto.request.PageableAccountFilterRequestDTO;
import com.joaopbessa.payments.producer.config.JPAConfiguration;
import com.joaopbessa.payments.producer.domain.entities.Account;
import com.joaopbessa.payments.producer.repositories.AccountRepository;

@DataJpaTest(properties = { "spring.cloud.vault.enabled=false", "spring.cloud.bootstrap.enabled=false" })
@Import(JPAConfiguration.class)
@DisplayName("AccountSpecification")
class AccountSpecificationTest {

    private static final int FIRST_PAGE = 0;
    private static final int PAGE_SIZE = 10;

    private static final String JOAO = "123456";
    private static final String MARIA = "654321";
    private static final String JOANA_INACTIVE = "123999";
    private static final String CARLOS = "777777";

    @Autowired
    private AccountRepository repository;

    @BeforeEach
    void setUp() {
        save(JOAO, "João Pedro", true);
        save(MARIA, "Maria Silva", true);
        save(JOANA_INACTIVE, "Joana Pereira", false);
        save(CARLOS, "Carlos Souza", true);
    }

    private void save(String number, String name, boolean active) {
        repository.saveAndFlush(Account.builder()
                .id(UUID.randomUUID())
                .number(number)
                .name(name)
                .active(active)
                .build());
    }

    private List<String> numbersMatching(String accountNumber, String accountName, Boolean active) {
        var filter = new PageableAccountFilterRequestDTO(accountNumber, accountName, active, FIRST_PAGE, PAGE_SIZE);
        return repository.findAll(AccountSpecification.filterBy(filter)).stream()
                .map(Account::getNumber)
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("Should return only the active accounts when no filter is given")
    void shouldReturnOnlyTheActiveAccountsWhenNoFilterIsGiven() {
        assertThat(numbersMatching(null, null, null)).containsExactly(JOAO, MARIA, CARLOS);
    }

    @Test
    @DisplayName("Should match the account number anywhere in the number")
    void shouldMatchTheAccountNumberAnywhereInTheNumber() {
        assertThat(numbersMatching("3456", null, null)).containsExactly(JOAO);
        assertThat(numbersMatching("77", null, null)).containsExactly(CARLOS);
    }

    @Test
    @DisplayName("Should leave inactive accounts out unless the filter asks for them")
    void shouldLeaveInactiveAccountsOutUnlessTheFilterAsksForThem() {
        assertThat(numbersMatching("123", null, null)).containsExactly(JOAO);
        assertThat(numbersMatching("123", null, false)).containsExactly(JOANA_INACTIVE);
    }

    @Test
    @DisplayName("Should return only the inactive accounts when active is false")
    void shouldReturnOnlyTheInactiveAccountsWhenActiveIsFalse() {
        assertThat(numbersMatching(null, null, false)).containsExactly(JOANA_INACTIVE);
    }

    @Test
    @DisplayName("Should return the active accounts when active is true")
    void shouldReturnTheActiveAccountsWhenActiveIsTrue() {
        assertThat(numbersMatching(null, null, true)).containsExactly(JOAO, MARIA, CARLOS);
    }

    @Test
    @DisplayName("Should match the account name ignoring case")
    void shouldMatchTheAccountNameIgnoringCase() {
        assertThat(numbersMatching(null, "SILVA", null)).containsExactly(MARIA);
        assertThat(numbersMatching(null, "joão", null)).containsExactly(JOAO);
    }

    @Test
    @DisplayName("Should combine the number and the name filters")
    void shouldCombineTheNumberAndTheNameFilters() {
        assertThat(numbersMatching("123", "pedro", null)).containsExactly(JOAO);
        assertThat(numbersMatching("654", "pedro", null)).isEmpty();
    }

    @Test
    @DisplayName("Should return nothing when no account matches")
    void shouldReturnNothingWhenNoAccountMatches() {
        assertThat(numbersMatching("000", null, null)).isEmpty();
        assertThat(numbersMatching(null, "zzz", null)).isEmpty();
    }

}
