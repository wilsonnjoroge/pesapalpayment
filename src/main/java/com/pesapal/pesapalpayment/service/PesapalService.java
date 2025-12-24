package com.pesapal.pesapalpayment.service;

import com.pesapal.pesapalpayment.dto.PesapalPaymentRequestDto;

public interface PesapalService {
    String initiatePayment(PesapalPaymentRequestDto dto, String merchantRef);
    void handleWebhook(String payload);
    String checkPaymentStatus(String merchantRef);
}
