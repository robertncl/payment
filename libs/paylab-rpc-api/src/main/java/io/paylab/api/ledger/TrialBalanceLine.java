package io.paylab.api.ledger;

import java.io.Serializable;
import java.math.BigDecimal;

/** Net (debits - credits) per account and currency, recomputed from the journal. */
public class TrialBalanceLine implements Serializable {

    private static final long serialVersionUID = 1L;

    private String accountId;
    private String accountType;
    private String currency;
    private BigDecimal debitTotal;
    private BigDecimal creditTotal;
    private BigDecimal net;

    public TrialBalanceLine() {}

    public TrialBalanceLine(
            String accountId,
            String accountType,
            String currency,
            BigDecimal debitTotal,
            BigDecimal creditTotal,
            BigDecimal net) {
        this.accountId = accountId;
        this.accountType = accountType;
        this.currency = currency;
        this.debitTotal = debitTotal;
        this.creditTotal = creditTotal;
        this.net = net;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getDebitTotal() {
        return debitTotal;
    }

    public void setDebitTotal(BigDecimal debitTotal) {
        this.debitTotal = debitTotal;
    }

    public BigDecimal getCreditTotal() {
        return creditTotal;
    }

    public void setCreditTotal(BigDecimal creditTotal) {
        this.creditTotal = creditTotal;
    }

    public BigDecimal getNet() {
        return net;
    }

    public void setNet(BigDecimal net) {
        this.net = net;
    }
}
