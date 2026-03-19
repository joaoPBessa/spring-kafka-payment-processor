package com.joaoPBessa.payments.producer.domain.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ACCOUNT")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Account {
	
	@Id
	@Column(name = "id", nullable = false)
	private UUID id;
	
	@Column(name = "NUMBER", nullable = false)
	private String number;
	
	@Column(name = "NAME")
	private String name;
	
	@Column(name = "ACTIVE", nullable = false)
	private Boolean active;
	
	@CreatedDate
	@Column(name = "CREATED_AT", nullable = false)
	private LocalDateTime createdAt;
	
	@LastModifiedDate
	@Column(name = "UPDATED_AT", nullable = false)
	private LocalDateTime updatedAt;

}
