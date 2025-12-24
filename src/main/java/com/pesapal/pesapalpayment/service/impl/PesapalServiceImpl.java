
package com.pesapal.pesapalpayment.service.impl;

import com.pesapal.pesapalpayment.dto.PesapalPaymentRequestDto;
import com.pesapal.pesapalpayment.entity.PesapalTransaction;
import com.pesapal.pesapalpayment.repository.PesapalTransactionRepository;
import com.pesapal.pesapalpayment.service.PesapalService;
import kong.unirest.Unirest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PesapalServiceImpl implements PesapalService {

    private final PesapalTransactionRepository repo;

    @Value("${pesapal.consumer-key}")
    private String consumerKey;

    @Value("${pesapal.consumer-secret}")
    private String consumerSecret;

    @Value("${pesapal.environment}")
    private String environment;

    @Value("${pesapal.ipn-id}")
    private String ipnId;  

    private String API_BASE;

    public PesapalServiceImpl(PesapalTransactionRepository repo) {
        this.repo = repo;
    }

    @Override
    public String initiatePayment(PesapalPaymentRequestDto dto, String merchantRef) {

        System.out.println("\n=== Pesapal Config ===\n");
        System.out.println("consumerKey: " + consumerKey + "\n");
        System.out.println("consumerSecret: " + consumerSecret + "\n");
        System.out.println("environment: " + environment + "\n");
        System.out.println("ipnId: " + ipnId + "\n");
        System.out.println("======================\n");

        // Determine API base URL
        API_BASE = "https://cybqa.pesapal.com/pesapalv3/api/"; // For TEST Environment. for live use "https://pay.pesapal.com/v3/api/"

        System.out.println("API_BASE: " + API_BASE + "\n");

        // STEP 1 → Get OAuth Token
        System.out.println("Requesting OAuth token...\n");
        String token = getAccessToken();
        System.out.println("OAuth Token: " + token + "\n");

        // STEP 2 → Build payment payload
        System.out.println("Building payment payload...\n");
        String payload = buildPaymentPayload(dto, merchantRef);
        System.out.println("Payment Payload: " + payload + "\n");

        // STEP 3 → Submit payment request
        var requestBody = Unirest
                .post(API_BASE + "Transactions/SubmitOrderRequest")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .body(payload)
                .asJson()
                .getBody()
                .getObject();

        System.out.println("Pesapal Response: " + requestBody + "\n");

        // STEP 4 → Extract redirect_url and order_tracking_id
        if (!requestBody.has("redirect_url") || !requestBody.has("order_tracking_id")) {
            System.err.println("Error: redirect_url or order_tracking_id not found in response.\n");
            throw new RuntimeException("Pesapal API response invalid: " + requestBody);
        }

        String redirectUrl = requestBody.getString("redirect_url");
        String orderTrackingId = requestBody.getString("order_tracking_id");

        System.out.println("Redirect URL: " + redirectUrl + "\n");
        System.out.println("Order Tracking ID: " + orderTrackingId + "\n");

        // STEP 5 → Save transaction in DB
        repo.save(PesapalTransaction.builder()
                .merchantReference(merchantRef)
                .pesapalOrderId(orderTrackingId)
                .customerId(dto.customerId())
                .amount(dto.amount())
                .currency(dto.currency())
                .description(dto.description())
                .status("PENDING")
                //.createdAt(LocalDateTime.now()) // Uncomment if needed
                .build()
        );

        return redirectUrl;
    }

    private String getAccessToken() {
        var resp = Unirest.post(API_BASE + "Auth/RequestToken")
                .header("Content-Type", "application/json")
                .body("{\"consumer_key\": \"" + consumerKey + "\", \"consumer_secret\": \"" + consumerSecret + "\"}")
                .asJson()
                .getBody();

        System.out.println("OAuth Response: " + resp + "\n");

        return resp.getObject().getString("token");
    }

    private String buildPaymentPayload(PesapalPaymentRequestDto dto, String merchantRef) {
        return """
        {
          "id": "%s",
          "currency": "%s",
          "amount": %d,
          "description": "%s",
          "callback_url": "%s",
          "notification_id": "%s",
          "billing_address": {
             "email_address": "customer@example.com",
             "phone_number": "%s",
             "country_code": "KE",
             "first_name": "Customer",
             "last_name": "User"
          }
        }
        """.formatted(
                merchantRef,
                dto.currency(),
                dto.amount(),
                dto.description(),
                dto.callbackUrl(),
                ipnId,            // IPN ID from properties
                dto.customerId()
        );
    }

    @Override
    public void handleWebhook(String payload) {
        System.out.println("\nReceived webhook payload: " + payload + "\n");
        // TODO: Parse webhook JSON and update transaction status
    }


    @Override
    public String checkPaymentStatus(String merchantRef) {

        System.out.println("\nQuerying Pesapal for merchantRef: " + merchantRef + "\n");

        PesapalTransaction txn = repo.findByMerchantReference(merchantRef)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + merchantRef));

        String token = getAccessToken();

        var response = Unirest.get(API_BASE + "Transactions/GetTransactionStatus")
                .header("Authorization", "Bearer " + token)
                .queryString("orderTrackingId", txn.getPesapalOrderId())
                .asJson()
                .getBody()
                .getObject();

        System.out.println("Pesapal transaction status response: " + response + "\n");

        String status = response.has("status") ? response.getString("status") : "UNKNOWN";

        txn.setStatus(status);
        repo.save(txn);

        return status;
    }
}
