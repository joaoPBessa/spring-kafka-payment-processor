package com.joaoPBessa.payments.consumer.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.joaoPBessa.payments.consumer.api.dto.response.DeadLetterMessageResponse;
import com.joaoPBessa.payments.consumer.dlt.DeadLetterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/dead-letters")
@RequiredArgsConstructor
public class DeadLetterController {

	private final DeadLetterService deadLetterService;

	@GetMapping
	public List<DeadLetterMessageResponse> oldestDeadLetters() {
		log.info("Received request to list the oldest dead letters");
		return deadLetterService.oldestMessages();
	}

}
