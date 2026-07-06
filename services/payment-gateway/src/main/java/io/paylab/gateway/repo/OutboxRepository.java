package io.paylab.gateway.repo;

import io.paylab.gateway.outbox.OutboxEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("select o from OutboxEvent o where o.publishedAt is null order by o.id asc")
    List<OutboxEvent> findUnpublished(Pageable pageable);
}
