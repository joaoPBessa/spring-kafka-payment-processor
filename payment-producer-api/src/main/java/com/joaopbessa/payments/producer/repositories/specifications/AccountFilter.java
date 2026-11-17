package com.joaopbessa.payments.producer.repositories.specifications;

public record AccountFilter(String accountNumber, String accountName, Boolean active) {

}
