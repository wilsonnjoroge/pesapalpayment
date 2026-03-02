package com.pesapal.pesapalpayment.controller;

import com.pesapal.pesapalpayment.dto.PesapalPaymentRequestDto;
import com.pesapal.pesapalpayment.dto.PesapalPaymentResponseDto;
import com.pesapal.pesapalpayment.dto.PaymentStatusResponseDto;
import com.pesapal.pesapalpayment.exception.PesapalApiException;
import com.pesapal.pesapalpayment.service.PesapalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/pesapal")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Tag(name = "Pesapal Payments", description = "Initiate M-Pesa STK-push payments via Pesapal and query their status")
public class PesapalController {

    private final PesapalService pesapalService;

    // ────────────────────────────────────────────────────────────────────────
    //  1.  INITIATE PAYMENT
    //      POST /api/pesapal/pay
    // ────────────────────────────────────────────────────────────────────────
    @Operation(
        summary = "Initiate a Pesapal payment (STK push)",
        description = """
            Submits an order to Pesapal.  Pesapal returns a `redirect_url` that \
            triggers an M-Pesa STK-push prompt on the customer's phone.
            
            **Sandbox test phone:** use any Safaricom number format e.g. `0712345678`.  
            **Sandbox callback_url:** can be any reachable URL or a RequestBin endpoint.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order submitted successfully",
            content = @Content(schema = @Schema(implementation = PesapalPaymentResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Validation error — missing required fields"),
        @ApiResponse(responseCode = "502", description = "Pesapal API rejected the request")
    })
    @PostMapping("/pay")
    public ResponseEntity<?> initiatePayment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Payment details",
                content = @Content(examples = @ExampleObject(value = """
                    {
                      "customerId": "CUST-001",
                      "amount": 1500,
                      "currency": "KES",
                      "description": "Order #12345 — Subscription renewal",
                      "email": "john.doe@example.com",
                      "phoneNumber": "0712345678",
                      "firstName": "John",
                      "lastName": "Doe"
                    }
                    """)))
            @Valid @RequestBody PesapalPaymentRequestDto dto) {

        log.info("[POST /pay] Received payment request for customerId={}", dto.customerId());
        PesapalPaymentResponseDto response = pesapalService.initiatePayment(dto);
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  2.  PAYMENT CALLBACK  (Pesapal iframe → your server after user action)
    //      GET /api/pesapal/callback?merchantRef=ORD-xxx
    //
    //  Pesapal redirects the user's browser here after they:
    //   • complete the STK push (enter PIN)
    //   • cancel the payment
    //   • let it time out
    //
    //  At this point we DON'T yet know the outcome — we just know the user
    //  is done with Pesapal's page. We immediately query GetTransactionStatus
    //  and return the result. This is what was causing POST / → 404.
    // ────────────────────────────────────────────────────────────────────────
    @Operation(
        summary = "Payment callback — Pesapal redirects user here after STK prompt",
        description = """
            **This endpoint is called by Pesapal's iframe**, not by your frontend directly.
            
            Pesapal redirects the user's browser to this URL after they interact with \
            the M-Pesa STK prompt (success, cancel, or timeout).
            
            The endpoint immediately queries Pesapal for the real outcome, updates the DB, \
            and returns the final status — so your frontend gets the answer in the redirect itself.
            
            **This is the URL that fixes `POST / → 404`.** \
            It is auto-constructed as: `{pesapal.base-url}/api/pesapal/callback?merchantRef={merchantRef}`
            
            **Simulate in Swagger:** paste a `merchantRef` from a previous `/pay` call.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Final payment status",
            content = @Content(schema = @Schema(implementation = PaymentStatusResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Transaction not found for this merchantRef")
    })
    @GetMapping("/callback")
    public ResponseEntity<PaymentStatusResponseDto> handleCallback(
            @Parameter(
                description = "The merchantReference that was embedded in the callbackUrl",
                example = "ORD-f99d4044-5b1b-4b83-bdb1-362bc8c5ac3b"
            )
            @RequestParam("merchantRef") String merchantRef) {

        log.info("[GET /callback] Pesapal redirected user back — merchantRef={}", merchantRef);
        PaymentStatusResponseDto response = pesapalService.checkAndRefreshStatus(merchantRef);
        log.info("[GET /callback] final status={} for merchantRef={}", response.status(), merchantRef);
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  2.  IPN CALLBACK  (Pesapal → your server)
    //      GET /api/pesapal/ipn
    //
    //  Pesapal fires this GET after the user interacts with the STK prompt:
    //   • paid successfully    → status_code=1 (COMPLETED)
    //   • cancelled            → status_code=2 (FAILED)
    //   • insufficient funds   → status_code=2 (FAILED)
    //   • still processing     → status_code=0 (PENDING)
    // ────────────────────────────────────────────────────────────────────────
    @Operation(
        summary = "IPN callback endpoint (called by Pesapal — not the frontend)",
        description = """
            Pesapal fires this after the M-Pesa prompt is resolved.  \
            Register this URL in the Pesapal dashboard as your IPN endpoint.
            
            Pesapal appends `OrderTrackingId` and `OrderMerchantReference` as query params.
            
            **Simulate in Swagger:**  
            Grab the `orderTrackingId` from the `/pay` response and paste it here \
            alongside the `merchantReference`.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "IPN processed — always return 200 to Pesapal"),
        @ApiResponse(responseCode = "404", description = "Transaction not found"),
        @ApiResponse(responseCode = "400", description = "OrderTrackingId mismatch — possible spoofed IPN")
    })
    @PostMapping("/ipn")
    public ResponseEntity<Map<String, String>> handleIpn(
            @Parameter(description = "Pesapal's internal tracking ID", example = "aab3e8cc-1234-5678-abcd-ef0123456789")
            @RequestParam("OrderTrackingId") String orderTrackingId,

            @Parameter(description = "Your merchant reference returned from /pay", example = "ORD-550e8400-e29b-41d4")
            @RequestParam("OrderMerchantReference") String merchantReference) {

        log.info("[GET /ipn] orderTrackingId={} merchantReference={}", orderTrackingId, merchantReference);
        pesapalService.processIpn(orderTrackingId, merchantReference);
        return ResponseEntity.ok(Map.of(
                "orderNotificationType", "IPNCHANGE",
                "orderTrackingId",       orderTrackingId,
                "orderMerchantReference", merchantReference,
                "status",                "200"
        ));
    }

    // ────────────────────────────────────────────────────────────────────────
    //  3.  CHECK PAYMENT STATUS
    //      GET /api/pesapal/status/{merchantRef}
    //
    //  Call this from your frontend/backend after the user is redirected back.
    //  If the transaction is still PENDING, it queries Pesapal directly and
    //  updates the DB before responding — so one call is always enough.
    // ────────────────────────────────────────────────────────────────────────
    @Operation(
        summary = "Check (and refresh) payment status",
        description = """
            Returns the current status of a transaction.
            
            If the status is still **PENDING**, this endpoint proactively queries \
            Pesapal's `GetTransactionStatus` API and updates the database before \
            returning — so the caller always gets the freshest possible answer \
            in a single request.
            
            **Status values:**  
            `PENDING` — STK prompt sent, no response yet  
            `SUCCESS` — M-Pesa confirmed payment  
            `FAILED`  — Cancelled, insufficient funds, or error
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status returned",
            content = @Content(schema = @Schema(implementation = PaymentStatusResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    @GetMapping("/status/{merchantRef}")
    public ResponseEntity<PaymentStatusResponseDto> checkStatus(
            @Parameter(description = "The merchantReference returned from /pay", example = "ORD-550e8400-e29b-41d4")
            @PathVariable String merchantRef) {

        log.info("[GET /status/{}]", merchantRef);
        PaymentStatusResponseDto response = pesapalService.checkAndRefreshStatus(merchantRef);
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  GLOBAL EXCEPTION HANDLERS  (controller-scoped)
    // ────────────────────────────────────────────────────────────────────────
    @ExceptionHandler(PesapalApiException.class)
    public ResponseEntity<Map<String, String>> handlePesapalError(PesapalApiException ex) {
        log.error("[PesapalApiException] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        log.error("[RuntimeException] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}