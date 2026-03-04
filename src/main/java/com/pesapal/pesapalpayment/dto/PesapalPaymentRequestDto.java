package com.pesapal.pesapalpayment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * Incoming payment request from the caller.
 * All fields map directly into the Pesapal SubmitOrderRequest payload.
 */
@Schema(description = "Payment initiation request")
public record PesapalPaymentRequestDto(

        @Schema(description = "Your internal customer identifier", example = "CUST-001")
        @NotBlank(message = "customerId is required")
        String customerId,

        @Schema(description = "Amount to charge — in major currency units (e.g. 1500 = KES 1,500)", example = "1500")
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        Long amount,

        @Schema(description = "ISO 4217 currency code", example = "KES")
        @NotBlank(message = "currency is required")
        String currency,

        @Schema(description = "Short description shown on the M-Pesa prompt", example = "Order #12345 — Subscription")
        @NotBlank(message = "description is required")
        String description,

        @Schema(description = "URL Pesapal redirects the user to after payment", example = "https://yourapp.com/payment/callback")
        @NotBlank(message = "callbackUrl is required")
        String callbackUrl,

        @Schema(description = "Customer email address", example = "john.doe@example.com")
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,

        @Schema(description = "Customer phone number (Safaricom format for STK push)", example = "0712345678")
        @NotBlank(message = "phoneNumber is required")
        String phoneNumber,

        @Schema(description = "Customer first name", example = "John")
        String firstName,

        @Schema(description = "Customer last name", example = "Doe")
        String lastName

) {}