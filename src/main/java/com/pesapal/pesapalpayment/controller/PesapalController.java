package com.pesapal.pesapalpayment.controller;

import com.pesapal.pesapalpayment.dto.PesapalPaymentRequestDto;
import com.pesapal.pesapalpayment.service.PesapalService;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pesapal")
@CrossOrigin(origins = "*")
public class PesapalController {

    private final PesapalService pesapalService;

    public PesapalController(PesapalService pesapalService) {
        this.pesapalService = pesapalService;
    }

    @PostMapping("/pay")
    public ResponseEntity<?> initiatePayment(@RequestBody PesapalPaymentRequestDto dto) {
        try {
            String merchantRef = "ORD-" + System.currentTimeMillis();
            String paymentUrl = pesapalService.initiatePayment(dto, merchantRef);

            return ResponseEntity.ok(Map.of(
                "merchantReference", merchantRef,
                "paymentUrl", paymentUrl,
                "status", "INITIATED"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload) {
        pesapalService.handleWebhook(payload);
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/ipn")
    public void handlePesapalIPN(@RequestBody String payload) {
        System.out.println("Received IPN payload: " + payload);
    }

    
    // Post-payment status check
    @GetMapping("/check-payment/{merchantRef}")
    public void checkPaymentStatus(@PathVariable String merchantRef, HttpServletResponse response) {
        System.out.println("\nChecking payment status for merchantRef: " + merchantRef + "\n");
        String status = pesapalService.checkPaymentStatus(merchantRef);
        System.out.println("Payment status from Pesapal: " + status + "\n");

        try {
            if ("COMPLETED".equalsIgnoreCase(status)) {
                response.sendRedirect("/api/pesapal/payment-success");
            } else {
                response.sendRedirect("/api/pesapal/payment-failed");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Payment success/failure endpoints (optional)
    @GetMapping("/payment-success")
    public ResponseEntity<String> paymentSuccess() {
        return ResponseEntity.ok("Payment Successful!");
    }

    @GetMapping("/payment-failed")
    public ResponseEntity<String> paymentFailed() {
        return ResponseEntity.ok("Payment Failed!");
    }


}
