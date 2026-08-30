package com.aarav.idempotentpaymentapi.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String id) {
        super("No payment found with id: " + id);
    }
}