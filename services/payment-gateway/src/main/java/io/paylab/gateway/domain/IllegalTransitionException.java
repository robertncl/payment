package io.paylab.gateway.domain;

/** Mapped to HTTP 409 by the API error handler. */
public class IllegalTransitionException extends RuntimeException {

    public IllegalTransitionException(PaymentStatus from, PaymentStatus to) {
        super("illegal payment transition " + from + " -> " + to);
    }
}
