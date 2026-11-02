package com.joaoPBessa.payments.consumer.domain.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.joaopBessa.payments.common.domain.PaymentMethod;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "PAYMENT")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment {

	@Id
	@Column(name = "ID")
	private UUID id;

	@Column(name = "TRANSACTION_ID", nullable = false, updatable = false, length = 36)
	private String transactionId;

	@Column(name = "SOURCE_ACCOUNT", nullable = false, updatable = false, length = 20)
	private String sourceAccount;

	@Column(name = "TARGET_ACCOUNT", nullable = false, updatable = false, length = 20)
	private String targetAccount;

	@Column(name = "AMOUNT", nullable = false, updatable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "CURRENCY", nullable = false, updatable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(name = "PAYMENT_METHOD", nullable = false, updatable = false, length = 20)
	private PaymentMethod paymentMethod;

	@Column(name = "EVENT_TIMESTAMP", nullable = false, updatable = false)
	private Instant eventTimestamp;

	@Column(name = "CREATED_AT", insertable = false, updatable = false)
	private Instant createdAt;

}
