package io.paylab.gateway.service;

import io.paylab.common.Money;
import java.math.BigDecimal;

/** MVP fee: flat 1% of principal in source currency, DECIMAL(20,4) rounding. */
public final class FeePolicy {

    public static final BigDecimal FEE_RATE = new BigDecimal("0.0100");

    public static BigDecimal feeFor(BigDecimal amount) {
        return Money.round4(amount.multiply(FEE_RATE));
    }

    private FeePolicy() {}
}
