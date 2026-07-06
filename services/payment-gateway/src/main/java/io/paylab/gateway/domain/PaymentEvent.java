package io.paylab.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Append-only transition timeline, drives the portal's payment-detail view (Phase 3). */
@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, length = 40)
    private String paymentId;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(name = "detail")
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected PaymentEvent() {}

    public PaymentEvent(String paymentId, PaymentStatus from, PaymentStatus to, String detail, Instant at) {
        this.paymentId = paymentId;
        this.fromStatus = from == null ? null : from.name();
        this.toStatus = to.name();
        this.detail = detail;
        this.occurredAt = at;
    }

    public Long getId() {
        return id;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
