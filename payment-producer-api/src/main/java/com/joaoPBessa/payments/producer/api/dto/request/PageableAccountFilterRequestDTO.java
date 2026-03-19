package com.joaoPBessa.payments.producer.api.dto.request;

import org.springframework.web.bind.annotation.BindParam;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PageableAccountFilterRequestDTO (
    
    @Size(min = 3, message = "Account number must be at least {min} characters long")
    @BindParam("account_number") 
    String accountNumber,
    
    @Size(min = 3, message = "Account name must be at least {min} characters long")
    @BindParam("account_name")
    String accountName,
    
    Boolean active,
    
    @NotNull(message = "Page index is required")
    @Min(value = 0, message = "Page index must be at least 0")
    Integer page,
    
    @NotNull(message = "Page size is required")
    @Min(value = 1, message = "Page size must be at least 1")
    Integer size
){
}