package com.joaoPBessa.payments.producer.controllers;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.joaoPBessa.payments.producer.api.dto.request.PaymentRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.response.PaymentResponseDTO;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
	
	@PostMapping
    public ResponseEntity<PaymentResponseDTO> createPayment(@RequestBody @Valid PaymentRequestDTO request) {
        String transactionId = UUID.randomUUID().toString();
        
        log.info("Receiving payment request. Transaction id: {}, source account: {}, amount: {}", 
                 transactionId, request.sourceAccount(), request.amount());

        // TODO: Chamar o Service que enviará para o Kafka
        
       var response = new PaymentResponseDTO(transactionId, request.sourceAccount(), request.targetAccount(), request.amount());
        
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(response);
    }

}
