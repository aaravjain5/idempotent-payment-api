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
import org.springframework.transaction.annotation.Transactional;

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

            // Already completed by an earlier request — return the cached result
            if (existing.isPresent() && existing.get().getResponseBody() != null) {
                return deserializeResponse(existing.get().getResponseBody());
            }

            if (existing.isEmpty()) {
                // Try to reserve this key. If two requests race here, only one insert succeeds.
                try {
                    IdempotencyKey reservation = new IdempotencyKey();
                    reservation.setKey(idempotencyKey);
                    idempotencyKeyRepository.saveAndFlush(reservation);
                } catch (DataIntegrityViolationException e) {
                    // Someone else reserved it a moment before us — wait for their result
                    return waitForCachedResponse(idempotencyKey);
                }
            } else {
                // Key is reserved but the original request hasn't finished yet
                return waitForCachedResponse(idempotencyKey);
            }
        }

        // We own this key (or none was provided) — safe to process the payment
        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setStatus("SUCCESS");

        Payment saved = paymentRepository.save(payment);

        PaymentResponse response = new PaymentResponse(
                saved.getId(), saved.getOrderId(), saved.getAmount(),
                saved.getCurrency(), saved.getStatus(), saved.getCreatedAt()
        );

        if (idempotencyKey != null) {
            IdempotencyKey record = idempotencyKeyRepository.findById(idempotencyKey).orElseThrow();
            record.setStatusCode(200);
            record.setResponseBody(serializeResponse(response));
            idempotencyKeyRepository.save(record);
        }

        return response;
    }

    public PaymentResponse getPaymentById(String id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new com.aarav.idempotentpaymentapi.exception.PaymentNotFoundException(id));
        return new PaymentResponse(
                payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getCurrency(), payment.getStatus(), payment.getCreatedAt()
        );
    }

    private PaymentResponse waitForCachedResponse(String key) {
        // Poll briefly for the in-flight request to finish, instead of creating a duplicate
        for (int i = 0; i < 10; i++) {
            Optional<IdempotencyKey> found = idempotencyKeyRepository.findById(key);
            if (found.isPresent() && found.get().getResponseBody() != null) {
                return deserializeResponse(found.get().getResponseBody());
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        throw new IllegalStateException("Timed out waiting for a concurrent request with the same idempotency key to complete");
    }

    private String serializeResponse(PaymentResponse response) {
        return objectMapper.writeValueAsString(response);
    }

    private PaymentResponse deserializeResponse(String json) {
        return objectMapper.readValue(json, PaymentResponse.class);
    }
}