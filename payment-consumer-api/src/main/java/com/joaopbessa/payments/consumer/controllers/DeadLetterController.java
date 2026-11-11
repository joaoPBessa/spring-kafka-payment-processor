package com.joaopbessa.payments.consumer.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.joaopbessa.payments.consumer.api.dto.request.ReplayDeadLettersRequest;
import com.joaopbessa.payments.consumer.api.dto.response.DeadLetterMessageResponse;
import com.joaopbessa.payments.consumer.api.dto.response.DeadLetterReplayResponse;
import com.joaopbessa.payments.consumer.dlt.DeadLetterReplayService;
import com.joaopbessa.payments.consumer.dlt.DeadLetterService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/dead-letters")
@RequiredArgsConstructor
public class DeadLetterController {

	private final DeadLetterService deadLetterService;
	private final DeadLetterReplayService deadLetterReplayService;

	@GetMapping
	public List<DeadLetterMessageResponse> oldestDeadLetters() {
		log.info("Received request to list the oldest dead letters");
		return deadLetterService.oldestMessages().stream().map(DeadLetterMessageResponse::from).toList();
	}

	@PostMapping("/replay")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public DeadLetterReplayResponse replay(@Valid @RequestBody ReplayDeadLettersRequest request) {
		log.info("Received request to replay {} dead letters", request.transactionIds().size());
		return DeadLetterReplayResponse.from(deadLetterReplayService.replay(request.transactionIds()));
	}

}
