package com.pesapal.pesapalpayment.service;

import com.pesapal.pesapalpayment.dto.PaymentStatusResponseDto;
import com.pesapal.pesapalpayment.dto.PesapalPaymentRequestDto;
import com.pesapal.pesapalpayment.dto.PesapalPaymentResponseDto;


public interface PesapalService {

    /**
     * Submits a new order to Pesapal and persists the transaction.
     * Returns the redirect URL (Pesapal iframe / STK-push trigger)
     * and the generated merchantReference.
     */
    PesapalPaymentResponseDto initiatePayment(PesapalPaymentRequestDto dto);

    /**
     * Handles the IPN callback that Pesapal fires after the user
     * completes, cancels, or fails the M-Pesa prompt.
     */
    void processIpn(String orderTrackingId, String merchantReference);

    /**
     * Actively polls Pesapal for the latest status of a PENDING transaction
     * and updates the DB. Safe to call from a status-check endpoint.
     */
    PaymentStatusResponseDto checkAndRefreshStatus(String merchantReference);
}