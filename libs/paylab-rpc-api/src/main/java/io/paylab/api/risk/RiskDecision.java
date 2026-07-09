package io.paylab.api.risk;

import java.io.Serializable;

/** Outcome of one assessment. Reason codes are stable strings the gateway records verbatim. */
public class RiskDecision implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String REASON_APPROVED = "APPROVED";
    public static final String REASON_DENYLISTED_PAYER = "DENYLISTED_PAYER";
    public static final String REASON_DENYLISTED_MERCHANT = "DENYLISTED_MERCHANT";
    public static final String REASON_CORRIDOR_UNSUPPORTED = "CORRIDOR_UNSUPPORTED";
    public static final String REASON_AMOUNT_OVER_CORRIDOR_CAP = "AMOUNT_OVER_CORRIDOR_CAP";
    public static final String REASON_VELOCITY_EXCEEDED = "VELOCITY_EXCEEDED";

    private boolean approved;
    private String reasonCode;
    private String detail;

    public RiskDecision() {}

    public RiskDecision(boolean approved, String reasonCode, String detail) {
        this.approved = approved;
        this.reasonCode = reasonCode;
        this.detail = detail;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
