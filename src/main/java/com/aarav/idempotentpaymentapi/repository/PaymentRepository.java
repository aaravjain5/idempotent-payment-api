package com.aarav.idempotentpaymentapi.repository;

import com.aarav.idempotentpaymentapi.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, String> {
}