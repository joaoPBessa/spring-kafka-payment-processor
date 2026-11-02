package com.joaoPBessa.payments.consumer.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.joaoPBessa.payments.consumer.domain.entities.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	boolean existsByTransactionId(String transactionId);

}
