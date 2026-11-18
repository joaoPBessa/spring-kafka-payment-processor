package com.joaopbessa.payments.auth.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.joaopbessa.payments.auth.domain.entities.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	/** Loads the user and its roles in one query, so the roles can be read outside a transaction. */
	@EntityGraph(attributePaths = "roles")
	Optional<AppUser> findByUsername(String username);

}
