package io.paylab.api.fx;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/** A locked FX rate snapshot. Capture must reference one of these — never a live rate. */
public class FxQuote implements Serializable {

    private static final long serialVersionUID = 1L;

    private String quoteId;
    private String sourceCurrency;
    private String targetCurrency;
    private BigDecimal rate;
    private Instant lockedAt;
    private Instant expiresAt;

    public FxQuote() {}

    public FxQuote(
            String quoteId,
            String sourceCurrency,
            String targetCurrency,
            BigDecimal rate,
            Instant lockedAt,
            Instant expiresAt) {
        this.quoteId = quoteId;
        this.sourceCurrency = sourceCurrency;
        this.targetCurrency = targetCurrency;
        this.rate = rate;
        this.lockedAt = lockedAt;
        this.expiresAt = expiresAt;
    }

    public String getQuoteId() {
        return quoteId;
    }

    public void setQuoteId(String quoteId) {
        this.quoteId = quoteId;
    }

    public String getSourceCurrency() {
        return sourceCurrency;
    }

    public void setSourceCurrency(String sourceCurrency) {
        this.sourceCurrency = sourceCurrency;
    }

    public String getTargetCurrency() {
        return targetCurrency;
    }

    public void setTargetCurrency(String targetCurrency) {
        this.targetCurrency = targetCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
