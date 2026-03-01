package com.pesapal.pesapalpayment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Maps the JSON body that Pesapal POSTs to your IPN endpoint.
 *
 * Pesapal sends:
 * {
 *   "OrderTrackingId":        "uuid",
 *   "OrderMerchantReference": "ORD-xxx",
 *   "OrderNotificationType":  "IPNCHANGE"
 * }
 */
@Schema(description = "IPN notification payload sent by Pesapal")
public record PesapalIpnRequestDto(

        @Schema(description = "Pesapal's internal tracking ID", example = "502bc88e-c1db-492b-8ba3-daae1877da55")
        @JsonProperty("OrderTrackingId")
        String orderTrackingId,

        @Schema(description = "Your merchant reference", example = "ORD-2c563fdf-f218-4d86-b003-bb2596009cef")
        @JsonProperty("OrderMerchantReference")
        String orderMerchantReference,

        @Schema(description = "Notification type — always IPNCHANGE", example = "IPNCHANGE")
        @JsonProperty("OrderNotificationType")
        String orderNotificationType

) {}