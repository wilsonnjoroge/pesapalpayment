package com.pesapal.pesapalpayment.dto;


public record PesapalPaymentRequestDto(
    String customerId,      
    Long amount,            
    String currency,        
    String description,
    String callbackUrl      
) {}