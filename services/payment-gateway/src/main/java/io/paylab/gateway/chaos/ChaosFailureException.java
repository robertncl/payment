package io.paylab.gateway.chaos;

/**
 * Deliberate fault injected by the forced-rollback gate (Phase 2). Thrown inside the Seata
 * global transaction after every branch has done its work, so the rollback of all branches
 * is observable from outside. Never enabled outside the lab (paylab.chaos.enabled).
 */
public class ChaosFailureException extends RuntimeException {

    public ChaosFailureException(String message) {
        super(message);
    }
}
