package io.paylab.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/** Money rules: DECIMAL(20,4) + ISO-4217 code, never floats (spec hard rule). */
public final class Money {

    public static final Set<String> SUPPORTED_CURRENCIES = Set.of("MYR", "SGD", "USD", "EUR", "CNY");
    public static final int SCALE = 4;

    /** Canonical rounding: scale 4, banker's rounding (HALF_EVEN) to avoid systematic drift. */
    public static BigDecimal round4(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_EVEN);
    }

    /** Rejects amounts that are null, non-positive, or would not fit DECIMAL(20,4) exactly. */
    public static void requireValidAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (amount.scale() > SCALE) {
            throw new IllegalArgumentException("amount scale must be <= " + SCALE + ": " + amount);
        }
        if (amount.precision() - amount.scale() > 16) {
            throw new IllegalArgumentException("amount exceeds DECIMAL(20,4): " + amount);
        }
    }

    /** Rejects any currency outside the five-corridor lab set. */
    public static void requireSupportedCurrency(String currency) {
        if (currency == null || !SUPPORTED_CURRENCIES.contains(currency)) {
            throw new IllegalArgumentException("unsupported currency: " + currency);
        }
    }

    private Money() {}
}
