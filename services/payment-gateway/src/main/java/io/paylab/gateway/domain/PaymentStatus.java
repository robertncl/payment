package io.paylab.gateway.domain;

public enum PaymentStatus {
    CREATED,
    RISK_APPROVED,
    RISK_DECLINED,
    CAPTURED,
    SETTLED,
    FAILED,
    REFUNDED
}
