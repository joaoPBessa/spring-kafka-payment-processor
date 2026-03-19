package com.joaoPBessa.payments.producer.repositories.specifications;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.joaoPBessa.payments.producer.api.dto.request.PageableAccountFilterRequestDTO;
import com.joaoPBessa.payments.producer.domain.entities.Account;

import jakarta.persistence.criteria.Predicate;

public class AccountSpecification {
	
	public static Specification<Account> filterBy(PageableAccountFilterRequestDTO filter) {
	    return (root, query, cb) -> {
	        List<Predicate> predicates = new ArrayList<>();

	        if (filter.accountNumber() != null) {
	            predicates.add(cb.like(root.get("number"), "%" + filter.accountNumber() + "%"));
	        }

	        if (filter.accountName() != null) {
	            predicates.add(cb.like(cb.lower(root.get("name")), "%" + filter.accountName().toLowerCase() + "%"));
	        }

	        if(filter.active() != null) {
	        	predicates.add(cb.equal(root.get("active"), filter.active()));
	        } else {
	        	predicates.add(cb.isTrue(root.get("active")));
	        }
	        
	        return cb.and(predicates.toArray(new Predicate[0]));
	    };
	}

}
