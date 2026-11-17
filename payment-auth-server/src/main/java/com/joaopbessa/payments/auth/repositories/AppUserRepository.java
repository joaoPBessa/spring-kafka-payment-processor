package com.joaopbessa.payments.auth.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.joaopbessa.payments.auth.domain.entities.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByUsername(String username);

}
