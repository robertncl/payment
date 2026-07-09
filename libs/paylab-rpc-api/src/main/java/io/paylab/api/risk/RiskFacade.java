package io.paylab.api.risk;

/**
 * Risk assessment for a payment at create time. Idempotent per paymentId: reassessing the
 * same payment returns the stored decision, so gateway retries cannot flip an outcome.
 */
public interface RiskFacade {

    RiskDecision assess(RiskAssessRequest request);
}
