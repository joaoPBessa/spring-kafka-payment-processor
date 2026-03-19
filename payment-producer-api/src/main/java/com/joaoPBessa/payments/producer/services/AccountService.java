package com.joaoPBessa.payments.producer.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.joaoPBessa.payments.producer.api.dto.request.PageableAccountFilterRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.response.AccountResponseDTO;
import com.joaoPBessa.payments.producer.domain.entities.Account;
import com.joaoPBessa.payments.producer.exceptions.AccountNotFoundException;
import com.joaoPBessa.payments.producer.exceptions.DuplicatedAccountException;
import com.joaoPBessa.payments.producer.repositories.AccountRepository;
import com.joaoPBessa.payments.producer.repositories.specifications.AccountSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {

	private final AccountRepository repository;

	public AccountResponseDTO save(Account account) {
		
		if(repository.existsByNumber(account.getNumber())) {
			throw new DuplicatedAccountException(String.format("Account number %s already exists", account.getNumber()));
		}
		
		return AccountResponseDTO.fromEntity(repository.save(account));
	}
	
	public void updateAccountName(String accountNumber, String accountName) {
		var account = findAccoutByNumberAndActive(accountNumber, true);
		
		account.setName(accountName);
		
		repository.save(account);
	}
	
	@Transactional
	public void deleteAccount(String accountNumber) {
		var linesAffected = repository.updateActiveByNumber(accountNumber, false);
		
		if(linesAffected <= 0) {
			throw new AccountNotFoundException(String.format("Account %s not found", accountNumber));
		}
		
	}
	
	public AccountResponseDTO findByNumber(String accountNumber) {
		return AccountResponseDTO.fromEntity(findAccoutByNumberAndActive(accountNumber, true));
	}
	
	public Page<AccountResponseDTO> findAccountsByFilter(PageableAccountFilterRequestDTO filter) {
		Pageable pageable = PageRequest.of(filter.page(), filter.size());
		
		Page<Account> result = repository.findAll(
				AccountSpecification.filterBy(filter),
				pageable);
		
		return new PageImpl<>(result.getContent().stream().map(AccountResponseDTO::fromEntity).toList(), pageable, result.getTotalElements());
	}
	
	private Account findAccoutByNumberAndActive(String accountNumber, boolean active) {
		
		return repository.findByNumberAndActive(accountNumber, active)
				.orElseThrow(() -> new AccountNotFoundException(String.format("Account %s not found", accountNumber)));
	}

}
