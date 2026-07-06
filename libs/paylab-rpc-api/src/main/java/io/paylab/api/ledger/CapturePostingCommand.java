package io.paylab.api.ledger;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Command to post the double-entry legs of a payment capture. Amounts are DECIMAL(20,4),
 * currencies ISO-4217. Posting is idempotent per (paymentId, entryType=CAPTURE).
 */
public class CapturePostingCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    private String paymentId;
    private String payerId;
    private String merchantId;
    private String sourceCurrency;
    private String targetCurrency;
    /** Principal in source currency (excludes fee). */
    private BigDecimal amount;
    /** Fee in source currency, revenue of the platform. */
    private BigDecimal feeAmount;
    /** Locked FX rate used for the conversion (from an FxQuote — never a live rate). */
    private BigDecimal fxRate;

    private String fxQuoteId;
    /** Principal converted to target currency = round4(amount * fxRate). */
    private BigDecimal targetAmount;

    public CapturePostingCommand() {}

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

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public void setFxRate(BigDecimal fxRate) {
        this.fxRate = fxRate;
    }

    public String getFxQuoteId() {
        return fxQuoteId;
    }

    public void setFxQuoteId(String fxQuoteId) {
        this.fxQuoteId = fxQuoteId;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
    }
}
