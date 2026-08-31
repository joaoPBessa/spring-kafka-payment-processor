package com.joaoPBessa.payments.producer.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.joaoPBessa.payments.producer.api.dto.request.PaymentRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.response.PaymentResponseDTO;
import com.joaoPBessa.payments.producer.services.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;

	@PostMapping
    public ResponseEntity<PaymentResponseDTO> createPayment(@RequestBody @Valid PaymentRequestDTO request) {
        log.info("Receiving payment request. Source account: {}, amount: {}",
                 request.sourceAccount(), request.amount());

        String transactionId = paymentService.publishPayment(request);

       var response = new PaymentResponseDTO(transactionId, request.sourceAccount(), request.targetAccount(), request.amount());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(response);
    }

}
