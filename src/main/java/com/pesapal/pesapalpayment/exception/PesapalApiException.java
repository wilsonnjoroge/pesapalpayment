package com.pesapal.pesapalpayment.exception;

/**
 * Thrown when the Pesapal API returns an unexpected HTTP status,
 * a missing required field, or a business-level error (e.g. invalid credentials).
 */
public class PesapalApiException extends RuntimeException {

    public PesapalApiException(String message) {
        super(message);
    }

    public PesapalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}