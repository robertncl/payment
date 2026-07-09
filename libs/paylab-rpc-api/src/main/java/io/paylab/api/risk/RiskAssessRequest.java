package io.paylab.api.risk;

import java.io.Serializable;
import java.math.BigDecimal;

public class RiskAssessRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String paymentId;
    private String payerId;
    private String merchantId;
    private String sourceCurrency;
    private String targetCurrency;
    private BigDecimal amount;

    public RiskAssessRequest() {}

    public RiskAssessRequest(
            String paymentId,
            String payerId,
            String merchantId,
            String sourceCurrency,
            String targetCurrency,
            BigDecimal amount) {
        this.paymentId = paymentId;
        this.payerId = payerId;
        this.merchantId = merchantId;
        this.sourceCurrency = sourceCurrency;
        this.targetCurrency = targetCurrency;
        this.amount = amount;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getPayerId() {
        return payerId;
    }

    public void setPayerId(String payerId) {
        this.payerId = payerId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
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
