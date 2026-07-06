package io.paylab.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(name = "payer_id", nullable = false, length = 64)
    private String payerId;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    @Column(name = "source_currency", nullable = false, length = 3)
    private String sourceCurrency;

    @Column(name = "target_currency", nullable = false, length = 3)
    private String targetCurrency;

    @Column(name = "amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal amount;

    @Column(name = "fee_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "target_amount", precision = 20, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "fx_quote_id", length = 64)
    private String fxQuoteId;

    @Column(name = "fx_rate", precision = 20, scale = 10)
    private BigDecimal fxRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Payment() {}

    public Payment(
            String id,
            String payerId,
            String merchantId,
            String sourceCurrency,
            String targetCurrency,
            BigDecimal amount,
            BigDecimal feeAmount,
            Instant now) {
        this.id = id;
        this.payerId = payerId;
        this.merchantId = merchantId;
        this.sourceCurrency = sourceCurrency;
        this.targetCurrency = targetCurrency;
        this.amount = amount;
        this.feeAmount = feeAmount;
        this.status = PaymentStatus.CREATED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** The single mutation path — every status change goes through the state machine. */
    public void transitionTo(PaymentStatus next, Instant now) {
        PaymentStateMachine.assertTransition(this.status, next);
        this.status = next;
        this.updatedAt = now;
    }

    public void attachCapture(String fxQuoteId, BigDecimal fxRate, BigDecimal targetAmount) {
        this.fxQuoteId = fxQuoteId;
        this.fxRate = fxRate;
        this.targetAmount = targetAmount;
    }

    public String getId() {
        return id;
    }

    public String getPayerId() {
        return payerId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getSourceCurrency() {
        return sourceCurrency;
    }

    public String getTargetCurrency() {
        return targetCurrency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public String getFxQuoteId() {
        return fxQuoteId;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
