package com.joaopbessa.payments.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.joaopbessa.payments.auth.domain.entities.AppUser;
import com.joaopbessa.payments.auth.repositories.AppUserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseUserDetailsService")
class DatabaseUserDetailsServiceTest {

    private static final String USERNAME = "producer-client";
    private static final String STORED_PASSWORD = "{bcrypt}" + new BCryptPasswordEncoder().encode("producer-client-demo");

    @Mock
    private AppUserRepository appUserRepository;

    private DatabaseUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new DatabaseUserDetailsService(appUserRepository);
    }

    private AppUser user(boolean enabled, Set<String> roles) {
        return AppUser.builder()
                .id(UUID.randomUUID())
                .username(USERNAME)
                .password(STORED_PASSWORD)
                .enabled(enabled)
                .roles(roles)
                .build();
    }

    @Test
    @DisplayName("Should hand over the stored password untouched so the password encoder can match it")
    void shouldHandOverTheStoredPassword() {
        when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user(true, Set.of("ACCOUNT_READ"))));

        UserDetails details = service.loadUserByUsername(USERNAME);

        assertThat(details.getUsername()).isEqualTo(USERNAME);
        assertThat(details.getPassword()).isEqualTo(STORED_PASSWORD);
        verify(appUserRepository, times(1)).findByUsername(USERNAME);
        verifyNoMoreInteractions(appUserRepository);
    }

    @Test
    @DisplayName("Should grant every role of the user as a ROLE_ authority")
    void shouldGrantEveryRoleAsAuthority() {
        when(appUserRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(user(true, Set.of("ACCOUNT_READ", "ACCOUNT_WRITE"))));

        UserDetails details = service.loadUserByUsername(USERNAME);

        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ACCOUNT_READ", "ROLE_ACCOUNT_WRITE");
        verify(appUserRepository, times(1)).findByUsername(USERNAME);
        verifyNoMoreInteractions(appUserRepository);
    }

    @Test
    @DisplayName("Should mark an enabled user as enabled")
    void shouldMarkEnabledUserAsEnabled() {
        when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user(true, Set.of())));

        assertThat(service.loadUserByUsername(USERNAME).isEnabled()).isTrue();
        verify(appUserRepository, times(1)).findByUsername(USERNAME);
        verifyNoMoreInteractions(appUserRepository);
    }

    @Test
    @DisplayName("Should mark a disabled user as disabled instead of hiding it")
    void shouldMarkDisabledUserAsDisabled() {
        when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user(false, Set.of("ACCOUNT_READ"))));

        assertThat(service.loadUserByUsername(USERNAME).isEnabled()).isFalse();
        verify(appUserRepository, times(1)).findByUsername(USERNAME);
        verifyNoMoreInteractions(appUserRepository);
    }

    @Test
    @DisplayName("Should fail with UsernameNotFoundException for an unknown username")
    void shouldFailForUnknownUsername() {
        when(appUserRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("nobody");
        verify(appUserRepository, times(1)).findByUsername("nobody");
        verifyNoMoreInteractions(appUserRepository);
    }

}
