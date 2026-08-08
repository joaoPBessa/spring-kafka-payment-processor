package com.joaoPBessa.payments.producer.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.joaoPBessa.payments.producer.api.dto.request.PageableAccountFilterRequestDTO;
import com.joaoPBessa.payments.producer.domain.entities.Account;
import com.joaoPBessa.payments.producer.exceptions.AccountNotFoundException;
import com.joaoPBessa.payments.producer.exceptions.DuplicatedAccountException;
import com.joaoPBessa.payments.producer.repositories.AccountRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService")
class AccountServiceTest {

    @Mock
    private AccountRepository repository;

    @InjectMocks
    private AccountService accountService;

    @Captor
    private ArgumentCaptor<Specification<Account>> specificationCaptor;

    @Test
    @DisplayName("save -> Success: Should persist a new account when the account number is not already taken")
    void shouldSaveAccountSuccessfully() {
        var account = Account.builder()
                .id(UUID.randomUUID())
                .number("123456")
                .name("João Pedro")
                .active(true)
                .build();
        var savedAccount = Account.builder()
                .id(account.getId())
                .number(account.getNumber())
                .name(account.getName())
                .active(account.getActive())
                .createdAt(LocalDateTime.now())
                .build();

        when(repository.existsByNumber(account.getNumber())).thenReturn(false);
        when(repository.save(account)).thenReturn(savedAccount);

        var response = accountService.save(account);

        assertThat(response.number()).isEqualTo(savedAccount.getNumber());
        assertThat(response.name()).isEqualTo(savedAccount.getName());
        assertThat(response.active()).isTrue();
        assertThat(response.createdAt()).isEqualTo(savedAccount.getCreatedAt());
        verify(repository).existsByNumber(account.getNumber());
        verify(repository).save(account);
    }

    @Test
    @DisplayName("save -> Validation: Should throw DuplicatedAccountException when the account number already exists")
    void shouldThrowDuplicatedAccountExceptionWhenAccountNumberAlreadyExists() {
        var account = Account.builder().id(UUID.randomUUID()).number("123456").name("João Pedro").active(true).build();

        when(repository.existsByNumber(account.getNumber())).thenReturn(true);

        assertThatThrownBy(() -> accountService.save(account))
                .isInstanceOf(DuplicatedAccountException.class)
                .hasMessage("Account number 123456 already exists");

        verify(repository).existsByNumber(account.getNumber());
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("updateAccountName -> Success: Should rename an active account")
    void shouldUpdateAccountNameSuccessfully() {
        String accountNumber = "123456";
        var existingAccount = Account.builder().id(UUID.randomUUID()).number(accountNumber).name("João Pedro").active(true).build();

        when(repository.findByNumberAndActive(accountNumber, true)).thenReturn(Optional.of(existingAccount));

        accountService.updateAccountName(accountNumber, "João Pedro Bessa");

        assertThat(existingAccount.getName()).isEqualTo("João Pedro Bessa");
        verify(repository).findByNumberAndActive(accountNumber, true);
        verify(repository).save(existingAccount);
    }

    @Test
    @DisplayName("updateAccountName -> Validation: Should throw AccountNotFoundException when the account is not active")
    void shouldThrowAccountNotFoundExceptionWhenUpdatingNonexistentAccount() {
        String accountNumber = "123456";

        when(repository.findByNumberAndActive(accountNumber, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateAccountName(accountNumber, "João Pedro Bessa"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account 123456 not found");

        verify(repository).findByNumberAndActive(accountNumber, true);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("deleteAccount -> Success: Should soft-delete an account by deactivating it")
    void shouldDeleteAccountSuccessfully() {
        String accountNumber = "123456";

        when(repository.updateActiveByNumber(accountNumber, false)).thenReturn(1);

        accountService.deleteAccount(accountNumber);

        verify(repository).updateActiveByNumber(accountNumber, false);
    }

    @Test
    @DisplayName("deleteAccount -> Validation: Should throw AccountNotFoundException when no rows are affected")
    void shouldThrowAccountNotFoundExceptionWhenDeletingNonexistentAccount() {
        String accountNumber = "123456";

        when(repository.updateActiveByNumber(accountNumber, false)).thenReturn(0);

        assertThatThrownBy(() -> accountService.deleteAccount(accountNumber))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account 123456 not found");

        verify(repository).updateActiveByNumber(accountNumber, false);
    }

    @Test
    @DisplayName("findByNumber -> Success: Should return the account when it exists and is active")
    void shouldFindAccountByNumberSuccessfully() {
        String accountNumber = "123456";
        var account = Account.builder()
                .id(UUID.randomUUID())
                .number(accountNumber)
                .name("João Pedro")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(repository.findByNumberAndActive(accountNumber, true)).thenReturn(Optional.of(account));

        var response = accountService.findByNumber(accountNumber);

        assertThat(response.number()).isEqualTo(accountNumber);
        assertThat(response.name()).isEqualTo(account.getName());
        assertThat(response.active()).isTrue();
        verify(repository).findByNumberAndActive(accountNumber, true);
    }

    @Test
    @DisplayName("findByNumber -> Validation: Should throw AccountNotFoundException when no active account matches")
    void shouldThrowAccountNotFoundExceptionWhenAccountNotFoundByNumber() {
        String accountNumber = "123456";

        when(repository.findByNumberAndActive(accountNumber, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findByNumber(accountNumber))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account 123456 not found");

        verify(repository).findByNumberAndActive(accountNumber, true);
    }

    @Test
    @DisplayName("findAccountsByFilter -> Success: Should return a page mapped from the repository result")
    void shouldReturnPaginatedAccountsMatchingFilter() {
        var filter = new PageableAccountFilterRequestDTO("123456", "João", true, 0, 10);
        Pageable expectedPageable = PageRequest.of(filter.page(), filter.size());
        var account = Account.builder()
                .id(UUID.randomUUID())
                .number("123456")
                .name("João Pedro")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        var repositoryPage = new PageImpl<>(List.of(account), expectedPageable, 1);

        when(repository.findAll(specificationCaptor.capture(), eq(expectedPageable))).thenReturn(repositoryPage);

        var result = accountService.findAccountsByFilter(filter);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).number()).isEqualTo("123456");
        // The Specification is built internally as a lambda in AccountSpecification and can't be
        // compared by equality; we only assert one was actually supplied to the repository call.
        assertThat(specificationCaptor.getValue()).isNotNull();
    }

}
