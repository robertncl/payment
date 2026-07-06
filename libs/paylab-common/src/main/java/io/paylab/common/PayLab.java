package io.paylab.common;

/** Cross-cutting constants. Domain types (Money, corridor codes) arrive in Phase 1. */
public final class PayLab {

    /** Header carrying the SOFATracer trace id on every external API response. */
    public static final String TRACE_ID_HEADER = "X-PayLab-Trace-Id";

    /** Header required on every mutating gateway endpoint. */
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private PayLab() {}
}
