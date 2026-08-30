package com.aarav.idempotentpaymentapi;

import com.aarav.idempotentpaymentapi.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    public void duplicateRequestWithSameIdempotencyKey_shouldCreateOnlyOnePayment() throws Exception {
        String requestBody = """
                {
                  "orderId": "order_test_001",
                  "amount": 250.0,
                  "currency": "INR"
                }
                """;

        long countBefore = paymentRepository.count();

        // First request
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "integration-test-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // Second request - same key, same body
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "integration-test-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        long countAfter = paymentRepository.count();

        // Only ONE new payment should have been created, despite two requests
        assertEquals(countBefore + 1, countAfter);
    }

    @Test
    public void differentIdempotencyKeys_shouldCreateSeparatePayments() throws Exception {
        String requestBody = """
                {
                  "orderId": "order_test_002",
                  "amount": 300.0,
                  "currency": "INR"
                }
                """;

        long countBefore = paymentRepository.count();

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "integration-test-key-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "integration-test-key-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        long countAfter = paymentRepository.count();

        // TWO new payments should have been created (different keys)
        assertEquals(countBefore + 2, countAfter);
    }
}