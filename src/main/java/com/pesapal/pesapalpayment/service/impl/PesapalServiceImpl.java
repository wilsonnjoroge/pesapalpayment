package com.pesapal.pesapalpayment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesapal.pesapalpayment.dto.PesapalPaymentRequestDto;
import com.pesapal.pesapalpayment.dto.PesapalPaymentResponseDto;
import com.pesapal.pesapalpayment.dto.PaymentStatusResponseDto;
import com.pesapal.pesapalpayment.entity.PaymentStatus;
import com.pesapal.pesapalpayment.entity.PesapalTransaction;
import com.pesapal.pesapalpayment.exception.PesapalApiException;
import com.pesapal.pesapalpayment.repository.PesapalTransactionRepository;
import com.pesapal.pesapalpayment.service.PesapalService;

import jakarta.annotation.PostConstruct;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PesapalServiceImpl implements PesapalService {

    private final PesapalTransactionRepository repo;
    private final ObjectMapper objectMapper;

    @Value("${pesapal.consumer-key}")
    private String consumerKey;

    @Value("${pesapal.consumer-secret}")
    private String consumerSecret;

    @Value("${pesapal.environment}")
    private String environment;

    @Value("${pesapal.ipn-id}")
    private String ipnId;

    @Value("${pesapal.base-url}")
    private String baseUrl;

    private String apiBase;

    // ── Token cache ─────────────────────────────────────────────────────────
    private String cachedToken;
    private Instant tokenExpiry = Instant.EPOCH;

    @PostConstruct
    void init() {
        // Accept "sandbox", "test", "dev", "development" → all route to sandbox
        // Only "production" or "prod" routes to live
        boolean isProduction = environment != null &&
                (environment.equalsIgnoreCase("production") || environment.equalsIgnoreCase("prod"));

        apiBase = isProduction
                ? "https://pay.pesapal.com/v3/api/"
                : "https://cybqa.pesapal.com/pesapalv3/api/";

        log.info("PesapalService initialised — environment='{}' → using {} ({})",
                environment, isProduction ? "PRODUCTION" : "SANDBOX", apiBase);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  1.  INITIATE PAYMENT
    // ────────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public PesapalPaymentResponseDto initiatePayment(PesapalPaymentRequestDto dto) {

        String merchantRef = "ORD-" + UUID.randomUUID();
        log.info("[initiatePayment] merchantRef={} customerId={} amount={} {}",
                merchantRef, dto.customerId(), dto.amount(), dto.currency());

        // STEP 1 — Auth token
        String token = getAccessToken();

        // STEP 2 — Build payload
        // callbackUrl is constructed server-side using baseUrl + merchantRef
        // Pesapal will POST/GET to this URL after the user completes the STK prompt
        String callbackUrl = baseUrl + "/api/pesapal/callback?merchantRef=" + merchantRef;
        String payload = buildPaymentPayload(dto, merchantRef, callbackUrl);
        log.debug("[initiatePayment] callbackUrl={}", callbackUrl);
        log.debug("[initiatePayment] payload={}", payload);

        // STEP 3 — Submit order to Pesapal
        HttpResponse<JsonNode> httpResp = Unirest
                .post(apiBase + "Transactions/SubmitOrderRequest")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .body(payload)
                .asJson();

        log.debug("[initiatePayment] Pesapal HTTP status={}", httpResp.getStatus());

        // Always log the raw body — Pesapal's error shape varies; this is critical for debugging
        String rawBody = httpResp.getBody() != null ? httpResp.getBody().toString() : "<empty>";
        log.debug("[initiatePayment] Pesapal raw response body={}", rawBody);

        if (httpResp.getStatus() != 200) {
            log.error("[initiatePayment] Pesapal rejected order — HTTP {} rawBody={}",
                    httpResp.getStatus(), rawBody);
            throw new PesapalApiException(
                    "Pesapal order submission failed — HTTP " + httpResp.getStatus() + " | body: " + rawBody);
        }

        var body = httpResp.getBody().getObject();

        // "200" is Pesapal's own inner status code inside the JSON body (separate from HTTP status)
        String pesapalStatus = body.optString("status", "");
        if (!"200".equals(pesapalStatus)) {
            // Pesapal wraps errors as: { "error": { "code": "...", "message": "..." } }
            // OR flat: { "message": "..." }
            String errorMsg;
            if (body.has("error") && !body.isNull("error")) {
                var errObj = body.optJSONObject("error");
                errorMsg = errObj != null
                        ? errObj.optString("code", "") + " — " + errObj.optString("message", "")
                        : body.optString("error", "Unknown error");
            } else {
                errorMsg = body.optString("message", body.optString("error", "Unknown error"));
            }
            log.error("[initiatePayment] Pesapal inner status={} error='{}' fullBody={}",
                    pesapalStatus, errorMsg, body);
            throw new PesapalApiException("Pesapal API error [status=" + pesapalStatus + "]: " + errorMsg);
        }

        if (!body.has("redirect_url") || !body.has("order_tracking_id")) {
            log.error("[initiatePayment] Missing fields in Pesapal response: {}", body);
            throw new PesapalApiException("Pesapal response missing redirect_url or order_tracking_id");
        }

        String redirectUrl      = body.getString("redirect_url");
        String orderTrackingId  = body.getString("order_tracking_id");

        log.info("[initiatePayment] order accepted — trackingId={} redirectUrl={}",
                orderTrackingId, redirectUrl);

        // STEP 4 — Persist transaction
        repo.save(PesapalTransaction.builder()
                .merchantReference(merchantRef)
                .pesapalOrderId(orderTrackingId)
                .customerId(dto.customerId())
                .amount(dto.amount())
                .currency(dto.currency())
                .description(dto.description())
                .status(PaymentStatus.PENDING)
                .build());

        return new PesapalPaymentResponseDto(merchantRef, orderTrackingId, redirectUrl, PaymentStatus.PENDING);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  2.  IPN CALLBACK  (Pesapal → your server)
    // ────────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void processIpn(String orderTrackingId, String merchantRef) {
        log.info("[processIpn] orderTrackingId={} merchantRef={}", orderTrackingId, merchantRef);

        PesapalTransaction txn = repo.findByMerchantReference(merchantRef)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + merchantRef));

        // Idempotency — skip if already terminal
        if (txn.getStatus() == PaymentStatus.SUCCESS || txn.getStatus() == PaymentStatus.FAILED) {
            log.info("[processIpn] already terminal status={}, skipping", txn.getStatus());
            return;
        }

        // Security — make sure the IPN trackingId matches what we stored
        if (!orderTrackingId.equals(txn.getPesapalOrderId())) {
            log.error("[processIpn] trackingId mismatch — expected={} got={}",
                    txn.getPesapalOrderId(), orderTrackingId);
            throw new PesapalApiException("OrderTrackingId mismatch for merchantRef=" + merchantRef);
        }

        applyRemoteStatus(txn, orderTrackingId, "[processIpn]");
        repo.save(txn);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  3.  MANUAL STATUS CHECK  (frontend polls after redirect)
    // ────────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public PaymentStatusResponseDto checkAndRefreshStatus(String merchantRef) {
        log.info("[checkAndRefreshStatus] merchantRef={}", merchantRef);

        PesapalTransaction txn = repo.findByMerchantReference(merchantRef)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + merchantRef));

        // Only call Pesapal if still PENDING — avoid unnecessary API calls
        if (txn.getStatus() == PaymentStatus.PENDING) {
            log.info("[checkAndRefreshStatus] status is PENDING, querying Pesapal...");
            applyRemoteStatus(txn, txn.getPesapalOrderId(), "[checkAndRefreshStatus]");
            repo.save(txn);
        }

        log.info("[checkAndRefreshStatus] final status={}", txn.getStatus());
        return new PaymentStatusResponseDto(
                txn.getMerchantReference(),
                txn.getPesapalOrderId(),
                txn.getStatus(),
                txn.getAmount(),
                txn.getCurrency()
        );
    }

    // ────────────────────────────────────────────────────────────────────────
    //  PRIVATE HELPERS
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Calls Pesapal GetTransactionStatus and maps the result onto the entity.
     * Extracted so both IPN and manual check share identical logic.
     */
    private void applyRemoteStatus(PesapalTransaction txn, String orderTrackingId, String caller) {
        String token = getAccessToken();

        HttpResponse<JsonNode> httpResp = Unirest
                .get(apiBase + "Transactions/GetTransactionStatus")
                .header("Authorization", "Bearer " + token)
                .queryString("orderTrackingId", orderTrackingId)
                .asJson();

        log.debug("{} GetTransactionStatus HTTP={}", caller, httpResp.getStatus());

        String rawStatusBody = httpResp.getBody() != null ? httpResp.getBody().toString() : "<empty>";
        log.debug("{} GetTransactionStatus raw response={}", caller, rawStatusBody);

        if (httpResp.getStatus() != 200) {
            log.error("{} Pesapal status check failed HTTP={} body={}", caller, httpResp.getStatus(), rawStatusBody);
            throw new PesapalApiException("Pesapal status check failed — HTTP " + httpResp.getStatus()
                    + " | body: " + rawStatusBody);
        }

        var response = httpResp.getBody().getObject();

        // Pesapal returns status_code as an integer: 1=COMPLETED, 2=FAILED, 3=REVERSED, 0=INVALID, 4=PENDING
        // optString handles both numeric and string values safely
        String statusCode = response.optString("status_code", "");
        String desc       = response.optString("description", "");
        String method     = response.optString("payment_method", "");

        log.info("{} Pesapal status_code={} description='{}' payment_method='{}'",
                caller, statusCode, desc, method);

        switch (statusCode) {
            case "1"  -> txn.setStatus(PaymentStatus.SUCCESS);   // COMPLETED
            case "2"  -> txn.setStatus(PaymentStatus.FAILED);    // FAILED
            case "3"  -> txn.setStatus(PaymentStatus.FAILED);    // REVERSED
            case "4"  -> txn.setStatus(PaymentStatus.PENDING);   // PENDING (explicit)
            default   -> {
                log.warn("{} Unrecognised status_code='{}' — keeping current status={}", caller, statusCode, txn.getStatus());
                // Do not change status — safer than blindly setting PENDING
            }
        }

        txn.setDescription(desc);
    }

    /**
     * Returns a valid OAuth token, using a cached one if still valid.
     * Pesapal tokens last 5 minutes; we refresh 30 s early to be safe.
     */
    private synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            log.debug("[getAccessToken] returning cached token");
            return cachedToken;
        }

        log.info("[getAccessToken] requesting new token from Pesapal");

        try {
            String body = objectMapper.writeValueAsString(
                    Map.of("consumer_key", consumerKey, "consumer_secret", consumerSecret));

            HttpResponse<JsonNode> httpResp = Unirest
                    .post(apiBase + "Auth/RequestToken")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .asJson();

            log.debug("[getAccessToken] HTTP status={}", httpResp.getStatus());

            String rawAuthBody = httpResp.getBody() != null ? httpResp.getBody().toString() : "<empty>";
            log.debug("[getAccessToken] raw response={}", rawAuthBody);

            if (httpResp.getStatus() != 200) {
                log.error("[getAccessToken] Token request failed — HTTP {} body={}",
                        httpResp.getStatus(), rawAuthBody);
                throw new PesapalApiException(
                        "Token request failed — HTTP " + httpResp.getStatus() + " | body: " + rawAuthBody);
            }

            var json = httpResp.getBody().getObject();
            if (!json.has("token")) {
                log.error("[getAccessToken] 'token' field missing in response: {}", json);
                throw new PesapalApiException("No token in Pesapal auth response");
            }

            cachedToken  = json.getString("token");
            tokenExpiry  = Instant.now().plusSeconds(270); // 4.5 min (token valid 5 min)
            log.info("[getAccessToken] new token obtained, expires at {}", tokenExpiry);
            return cachedToken;

        } catch (PesapalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new PesapalApiException("Failed to serialise token request: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the SubmitOrderRequest JSON body from the DTO.
     * callbackUrl is passed separately — it is always constructed server-side
     * from pesapal.base-url + merchantRef, never trusted from the client.
     */
    private String buildPaymentPayload(PesapalPaymentRequestDto dto, String merchantRef, String callbackUrl) {
        String firstName = (dto.firstName() != null && !dto.firstName().isBlank())
                ? dto.firstName() : "Unknown";
        String lastName  = (dto.lastName()  != null && !dto.lastName().isBlank())
                ? dto.lastName()  : "User";

        return """
        {
          "id": "%s",
          "currency": "%s",
          "amount": %.2f,
          "description": "%s",
          "callback_url": "%s",
          "notification_id": "%s",
          "billing_address": {
            "email_address": "%s",
            "phone_number": "%s",
            "country_code": "KE",
            "first_name": "%s",
            "last_name": "%s"
          }
        }
        """.formatted(
                merchantRef,
                dto.currency(),
                dto.amount().doubleValue(),
                dto.description(),
                callbackUrl,
                ipnId,
                dto.email(),
                dto.phoneNumber(),
                firstName,
                lastName
        );
    }
}