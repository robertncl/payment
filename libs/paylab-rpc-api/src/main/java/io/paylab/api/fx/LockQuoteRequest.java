package io.paylab.api.fx;

import java.io.Serializable;
import java.math.BigDecimal;

public class LockQuoteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sourceCurrency;
    private String targetCurrency;
    private BigDecimal amount;

    public LockQuoteRequest() {}

    public LockQuoteRequest(String sourceCurrency, String targetCurrency, BigDecimal amount) {
        this.sourceCurrency = sourceCurrency;
        this.targetCurrency = targetCurrency;
        this.amount = amount;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
