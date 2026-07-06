package io.paylab.gateway.domain;

import static io.paylab.gateway.domain.PaymentStatus.CAPTURED;
import static io.paylab.gateway.domain.PaymentStatus.CREATED;
import static io.paylab.gateway.domain.PaymentStatus.FAILED;
import static io.paylab.gateway.domain.PaymentStatus.REFUNDED;
import static io.paylab.gateway.domain.PaymentStatus.RISK_APPROVED;
import static io.paylab.gateway.domain.PaymentStatus.RISK_DECLINED;
import static io.paylab.gateway.domain.PaymentStatus.SETTLED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PaymentStateMachineTest {

    @Test
    void happyPathIsLegal() {
        assertTrue(PaymentStateMachine.canTransition(CREATED, RISK_APPROVED));
        assertTrue(PaymentStateMachine.canTransition(RISK_APPROVED, CAPTURED));
        assertTrue(PaymentStateMachine.canTransition(CAPTURED, SETTLED));
    }

    @Test
    void branchesAreLegal() {
        assertTrue(PaymentStateMachine.canTransition(CREATED, RISK_DECLINED));
        assertTrue(PaymentStateMachine.canTransition(CREATED, FAILED));
        assertTrue(PaymentStateMachine.canTransition(RISK_APPROVED, FAILED));
        assertTrue(PaymentStateMachine.canTransition(CAPTURED, REFUNDED));
    }

    @Test
    void terminalStatesAllowNothing() {
        for (PaymentStatus terminal : Set.of(RISK_DECLINED, SETTLED, FAILED, REFUNDED)) {
            for (PaymentStatus to : PaymentStatus.values()) {
                assertFalse(
                        PaymentStateMachine.canTransition(terminal, to), terminal + " -> " + to + " must be illegal");
            }
        }
    }

    @Test
    void skippingStatesIsIllegal() {
        assertFalse(PaymentStateMachine.canTransition(CREATED, CAPTURED));
        assertFalse(PaymentStateMachine.canTransition(CREATED, SETTLED));
        assertFalse(PaymentStateMachine.canTransition(RISK_APPROVED, SETTLED));
        assertFalse(PaymentStateMachine.canTransition(RISK_APPROVED, REFUNDED));
        assertThrows(IllegalTransitionException.class, () -> PaymentStateMachine.assertTransition(CREATED, CAPTURED));
    }
}
