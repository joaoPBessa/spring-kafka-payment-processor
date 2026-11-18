package com.joaopbessa.payments.auth.domain.entities;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A person who can sign in. The authorization server only reads this entity: the database role it runs as has no
 * write privilege on the user tables.
 */
@Entity
@Table(name = "APP_USER")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AppUser {

	@Id
	@Column(name = "ID")
	private UUID id;

	@Column(name = "USERNAME", nullable = false, updatable = false, length = 50)
	private String username;

	/** The stored value, prefixed with the id of the encoding that produced it (for example {@code {base64}...}). */
	@Column(name = "PASSWORD", nullable = false, length = 200)
	private String password;

	@Column(name = "ENABLED", nullable = false)
	private boolean enabled;

	@Column(name = "CREATED_AT", insertable = false, updatable = false)
	private Instant createdAt;

	@ElementCollection
	@CollectionTable(name = "APP_USER_ROLE", joinColumns = @JoinColumn(name = "USER_ID"))
	@Column(name = "ROLE", nullable = false, length = 50)
	private Set<String> roles;

}
