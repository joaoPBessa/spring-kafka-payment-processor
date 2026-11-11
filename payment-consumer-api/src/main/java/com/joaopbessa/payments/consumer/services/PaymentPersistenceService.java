package com.joaopbessa.payments.consumer.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.joaopbessa.payments.consumer.domain.entities.Payment;
import com.joaopbessa.payments.consumer.mappers.PaymentEventMapper;
import com.joaopbessa.payments.consumer.metrics.PaymentConsumerMetrics;
import com.joaopbessa.payments.consumer.repositories.PaymentRepository;
import com.joaopbessa.payments.common.avro.PaymentEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPersistenceService {

	private final PaymentRepository paymentRepository;
	private final PaymentEventMapper paymentEventMapper;
	private final PaymentConsumerMetrics metrics;

	public void persist(PaymentEvent event) {
		String transactionId = event.getTransactionId();

		if (paymentRepository.existsByTransactionId(transactionId)) {
			logAlreadyPersisted(transactionId);
			metrics.duplicateIgnored();
			return;
		}

		Payment payment = paymentEventMapper.toEntity(event);
		try {
			paymentRepository.saveAndFlush(payment);
			metrics.paymentPersisted();
			log.info("Payment persisted. Transaction id: {}", transactionId);
		} catch (DataIntegrityViolationException e) {
			if (!paymentRepository.existsByTransactionId(transactionId)) {
				throw e;
			}
			logAlreadyPersisted(transactionId);
			metrics.duplicateIgnored();
		}
	}

	private void logAlreadyPersisted(String transactionId) {
		log.warn("Payment already persisted, skipping duplicate event. Transaction id: {}", transactionId);
	}

}
