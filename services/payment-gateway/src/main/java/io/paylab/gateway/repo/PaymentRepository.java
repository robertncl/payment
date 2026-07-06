package io.paylab.gateway.repo;

import io.paylab.gateway.domain.Payment;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    List<Payment> findByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable pageable);

    List<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
