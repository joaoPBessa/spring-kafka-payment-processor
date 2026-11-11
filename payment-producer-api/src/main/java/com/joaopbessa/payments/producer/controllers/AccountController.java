package com.joaopbessa.payments.producer.controllers;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.joaopbessa.payments.producer.api.dto.request.CreateAccountRequestDTO;
import com.joaopbessa.payments.producer.api.dto.request.PageableAccountFilterRequestDTO;
import com.joaopbessa.payments.producer.api.dto.request.UpdateAccountRequestDTO;
import com.joaopbessa.payments.producer.api.dto.response.AccountResponseDTO;
import com.joaopbessa.payments.producer.services.AccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
	
	private final AccountService service;

	@PostMapping("/")
	public ResponseEntity<AccountResponseDTO> createAccount(@RequestBody @Valid CreateAccountRequestDTO request) {
		log.info("Received request to create account, number: {}, name: {}",
				request.accountNumber(), request.accountName());
		
		var savedAccount = service.save(request.toEntity());
		
		URI location = ServletUriComponentsBuilder
				.fromCurrentRequest()
				.path("/{accountNumber}")
				.buildAndExpand(savedAccount.number())
				.toUri();
		
		return ResponseEntity
				.created(location)
				.body(savedAccount);
	}
	
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PatchMapping("/{accountNumber}")
	public void updateAccount(@PathVariable String accountNumber, @Valid @RequestBody UpdateAccountRequestDTO request) {
		log.info("Received request to rename account number: {}, name: {}", accountNumber, request.accountName());
		service.updateAccountName(accountNumber, request.accountName());
	}
	
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@DeleteMapping("/{accountNumber}")
	public void deleteAccount(@PathVariable String accountNumber) {
		log.info("Received request to delete account number: {}", accountNumber);
		service.deleteAccount(accountNumber);
	}
	
	@GetMapping("/{accountNumber}")
	public ResponseEntity<AccountResponseDTO> getAccountByNumber(@PathVariable String accountNumber) {
		log.info("Finding account by number: {}", accountNumber);
		return ResponseEntity.ok(service.findByNumber(accountNumber));
	}
	
	@GetMapping
	public Page<AccountResponseDTO> getAll(@Valid PageableAccountFilterRequestDTO pageableRequest) {
		log.info("Finding pageable account with size: {}, page: {}, number: {}, name: {}",
				pageableRequest.size(),
				pageableRequest.page(),
				pageableRequest.accountNumber(),
				pageableRequest.accountName());
		return service.findAccountsByFilter(pageableRequest);
	}

}
