package com.joaoPBessa.payments.producer.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.joaoPBessa.payments.producer.domain.entities.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {

    Optional<Account> findByNumberAndActive(String accountNumber, boolean active);

    boolean existsByNumber(String accountNumber);

    @Modifying
    @Query("UPDATE Account a SET a.active = :active WHERE a.number = :accountNumber")
    int updateActiveByNumber(String accountNumber, boolean active);
	
}
