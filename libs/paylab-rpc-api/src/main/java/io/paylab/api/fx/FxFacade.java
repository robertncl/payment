package io.paylab.api.fx;

/**
 * FX quoting over SOFARPC/bolt. Rates come from the static in-repo table (MYR/SGD/USD/EUR/CNY);
 * a locked quote is immutable and valid for 60 seconds.
 */
public interface FxFacade {

    /** Locks the current table rate for the pair; throws IllegalArgumentException for unknown pairs. */
    FxQuote lockQuote(LockQuoteRequest request);

    /** Returns the quote if it exists and has not expired, else null. */
    FxQuote getQuote(String quoteId);
}
