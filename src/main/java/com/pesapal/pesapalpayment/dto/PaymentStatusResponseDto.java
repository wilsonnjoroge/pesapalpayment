package com.pesapal.pesapalpayment.dto;

import com.pesapal.pesapalpayment.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Current status of a transaction — returned from the status-check endpoint.
 */
@Schema(description = "Current transaction status")
public record PaymentStatusResponseDto(

        @Schema(description = "Your merchant reference", example = "ORD-550e8400-e29b-41d4")
        String merchantReference,

        @Schema(description = "Pesapal's tracking ID", example = "aab3e8cc-1234-5678-abcd-ef0123456789")
        String orderTrackingId,

        @Schema(description = """
                PENDING  — STK prompt sent, awaiting customer action
                SUCCESS  — M-Pesa confirmed the payment
                FAILED   — Customer cancelled, insufficient funds, or error
                """,
                example = "SUCCESS")
        PaymentStatus status,

        @Schema(description = "Transaction amount", example = "1500")
        Long amount,

        @Schema(description = "Currency code", example = "KES")
        String currency

) {}