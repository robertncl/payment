package io.paylab.gateway.domain;

import java.util.Map;
import java.util.Set;

/**
 * Payment lifecycle (spec §2): CREATED → RISK_APPROVED → CAPTURED → SETTLED with branches
 * RISK_DECLINED, FAILED, REFUNDED. Illegal transitions are rejected, not silently ignored.
 */
public final class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            PaymentStatus.CREATED,
                    Set.of(PaymentStatus.RISK_APPROVED, PaymentStatus.RISK_DECLINED, PaymentStatus.FAILED),
            PaymentStatus.RISK_APPROVED, Set.of(PaymentStatus.CAPTURED, PaymentStatus.FAILED),
            PaymentStatus.CAPTURED, Set.of(PaymentStatus.SETTLED, PaymentStatus.REFUNDED),
            PaymentStatus.RISK_DECLINED, Set.of(),
            PaymentStatus.SETTLED, Set.of(),
            PaymentStatus.FAILED, Set.of(),
            PaymentStatus.REFUNDED, Set.of());

    /** True when {@code from → to} is an edge in the lifecycle graph above. */
    public static boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Guard used before any side effect of a transition: throws {@link
     * IllegalTransitionException} (mapped to HTTP 409) instead of returning false, so callers
     * cannot forget to check.
     */
    public static void assertTransition(PaymentStatus from, PaymentStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalTransitionException(from, to);
        }
    }

    private PaymentStateMachine() {}
}
