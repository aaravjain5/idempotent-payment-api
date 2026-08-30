package com.aarav.idempotentpaymentapi.service;

import com.aarav.idempotentpaymentapi.dto.PaymentRequest;
import com.aarav.idempotentpaymentapi.dto.PaymentResponse;
import com.aarav.idempotentpaymentapi.model.Payment;
import com.aarav.idempotentpaymentapi.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public PaymentResponse processPayment(PaymentRequest request) {
        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setStatus("SUCCESS"); // for now, always succeed — real gateway logic comes later

        Payment saved = paymentRepository.save(payment);

        return new PaymentResponse(
                saved.getId(),
                saved.getOrderId(),
                saved.getAmount(),
                saved.getCurrency(),
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }
}