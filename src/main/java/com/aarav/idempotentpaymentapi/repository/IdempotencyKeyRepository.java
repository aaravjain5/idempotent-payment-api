package com.aarav.idempotentpaymentapi.repository;

import com.aarav.idempotentpaymentapi.model.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
}