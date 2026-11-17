package com.joaopbessa.payments.auth.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.joaopbessa.payments.auth.domain.entities.AppUser;
import com.joaopbessa.payments.auth.repositories.AppUserRepository;
import com.joaopbessa.payments.common.security.TokenClaims;

import lombok.RequiredArgsConstructor;

/**
 * Loads the people who sign in from the {@code APP_USER} tables. The stored password goes to Spring Security as is;
 * the {@code PasswordEncoder} bean decides how to match it from the id prefix.
 */
@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

	private final AppUserRepository appUserRepository;

	@Override
	public UserDetails loadUserByUsername(String username) {
		AppUser user = appUserRepository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
		return User.withUsername(user.getUsername())
				.password(user.getPassword())
				.disabled(!user.isEnabled())
				.authorities(user.getRoles().stream()
						.map(role -> new SimpleGrantedAuthority(TokenClaims.AUTHORITY_PREFIX + role))
						.toList())
				.build();
	}

}
