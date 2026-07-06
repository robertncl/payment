package io.paylab.gateway.repo;

import io.paylab.gateway.idempotency.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {}
