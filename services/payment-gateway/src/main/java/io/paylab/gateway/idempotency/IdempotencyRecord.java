package io.paylab.gateway.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @Id
    @Column(name = "idem_key", length = 100)
    private String key;

    @Column(name = "endpoint", nullable = false, length = 80)
    private String endpoint;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    /** null while the first request is still executing. */
    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "payment_id", length = 40)
    private String paymentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String key, String endpoint, String requestHash, Instant now) {
        this.key = key;
        this.endpoint = endpoint;
        this.requestHash = requestHash;
        this.createdAt = now;
    }

    public void complete(int httpStatus, String responseBody, String paymentId) {
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.paymentId = paymentId;
    }

    public String getKey() {
        return key;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getPaymentId() {
        return paymentId;
    }
}
