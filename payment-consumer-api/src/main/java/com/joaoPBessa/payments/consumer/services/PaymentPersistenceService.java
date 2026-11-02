package com.joaoPBessa.payments.consumer.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.joaoPBessa.payments.consumer.domain.entities.Payment;
import com.joaoPBessa.payments.consumer.mappers.PaymentEventMapper;
import com.joaoPBessa.payments.consumer.repositories.PaymentRepository;
import com.joaopBessa.payments.common.avro.PaymentEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPersistenceService {

	private final PaymentRepository paymentRepository;
	private final PaymentEventMapper paymentEventMapper;

	public void persist(PaymentEvent event) {
		String transactionId = event.getTransactionId();

		if (paymentRepository.existsByTransactionId(transactionId)) {
			logAlreadyPersisted(transactionId);
			return;
		}

		Payment payment = paymentEventMapper.toEntity(event);
		try {
			paymentRepository.saveAndFlush(payment);
			log.info("Payment persisted. Transaction id: {}", transactionId);
		} catch (DataIntegrityViolationException e) {
			if (!paymentRepository.existsByTransactionId(transactionId)) {
				throw e;
			}
			logAlreadyPersisted(transactionId);
		}
	}

	private void logAlreadyPersisted(String transactionId) {
		log.warn("Payment already persisted, skipping duplicate event. Transaction id: {}", transactionId);
	}

}
