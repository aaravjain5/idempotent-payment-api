package com.aarav.idempotentpaymentapi.service;

import com.aarav.idempotentpaymentapi.dto.PaymentRequest;
import com.aarav.idempotentpaymentapi.dto.PaymentResponse;
import com.aarav.idempotentpaymentapi.model.IdempotencyKey;
import com.aarav.idempotentpaymentapi.model.Payment;
import com.aarav.idempotentpaymentapi.repository.IdempotencyKeyRepository;
import com.aarav.idempotentpaymentapi.repository.PaymentRepository;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Autowired
    public PaymentService(PaymentRepository paymentRepository, IdempotencyKeyRepository idempotencyKeyRepository) {
        this.paymentRepository = paymentRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<IdempotencyKey> existing = idempotencyKeyRepository.findById(idempotencyKey);
            if (existing.isPresent()) {
                return deserializeResponse(existing.get().getResponseBody());
            }
        }

        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setStatus("SUCCESS");

        Payment saved = paymentRepository.save(payment);

        PaymentResponse response = new PaymentResponse(
                saved.getId(),
                saved.getOrderId(),
                saved.getAmount(),
                saved.getCurrency(),
                saved.getStatus(),
                saved.getCreatedAt()
        );

        if (idempotencyKey != null) {
            try {
                IdempotencyKey keyRecord = new IdempotencyKey();
                keyRecord.setKey(idempotencyKey);
                keyRecord.setStatusCode(200);
                keyRecord.setResponseBody(serializeResponse(response));
                idempotencyKeyRepository.save(keyRecord);
            } catch (DataIntegrityViolationException e) {
                // Another concurrent request already saved this key first —
                // return that request's cached response instead of our own
                Optional<IdempotencyKey> raceWinner = idempotencyKeyRepository.findById(idempotencyKey);
                if (raceWinner.isPresent()) {
                    return deserializeResponse(raceWinner.get().getResponseBody());
                }
            }
        }

        return response;
    }

    private String serializeResponse(PaymentResponse response) {
        return objectMapper.writeValueAsString(response);
    }

    private PaymentResponse deserializeResponse(String json) {
        return objectMapper.readValue(json, PaymentResponse.class);
    }
}