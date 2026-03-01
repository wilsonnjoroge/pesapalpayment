package com.pesapal.pesapalpayment.dto;

import com.pesapal.pesapalpayment.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned after a successful order submission to Pesapal.
 */
@Schema(description = "Payment initiation response")
public record PesapalPaymentResponseDto(

        @Schema(description = "Your unique order reference — use this for all subsequent status queries",
                example = "ORD-550e8400-e29b-41d4-a716-446655440000")
        String merchantReference,

        @Schema(description = "Pesapal's internal tracking ID for this transaction",
                example = "aab3e8cc-1234-5678-abcd-ef0123456789")
        String orderTrackingId,

        @Schema(description = "Pesapal URL to redirect the user to (triggers STK push on their phone)",
                example = "https://cybqa.pesapal.com/pesapaliframe/PesapalIframe/Index?OrderTrackingId=aab3e8cc...")
        String paymentUrl,

        @Schema(description = "Initial transaction status — always PENDING at this stage",
                example = "PENDING")
        PaymentStatus status

) {}