package io.paylab.fx.quote;

import io.paylab.api.fx.FxQuote;
import io.paylab.api.fx.LockQuoteRequest;
import io.paylab.fx.rates.StaticRateTable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Locks rate snapshots with a 60s TTL. Quotes are held in memory: acceptable for the lab
 * (a restart voids open quotes and capture re-locks); durability is documented debt.
 */
@Service
public class QuoteService {

    public static final Duration TTL = Duration.ofSeconds(60);

    private final StaticRateTable rateTable;
    private final Clock clock;
    private final Map<String, FxQuote> quotes = new ConcurrentHashMap<>();

    public QuoteService(StaticRateTable rateTable, Clock clock) {
        this.rateTable = rateTable;
        this.clock = clock;
    }

    /**
     * Freezes the current table rate into a quote valid for {@link #TTL}. The gateway locks a
     * quote at capture time so the rate written to the payment and the rate the ledger posts
     * at are the same number, even if the table changes in between.
     */
    public FxQuote lock(LockQuoteRequest request) {
        if (request == null
                || request.getAmount() == null
                || request.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Instant now = clock.instant();
        FxQuote quote = new FxQuote(
                "quo_" + UUID.randomUUID(),
                request.getSourceCurrency(),
                request.getTargetCurrency(),
                rateTable.rate(request.getSourceCurrency(), request.getTargetCurrency()),
                now,
                now.plus(TTL));
        quotes.put(quote.getQuoteId(), quote);
        return quote;
    }

    /** Returns null when unknown or expired — the caller must re-lock, never reuse. */
    public FxQuote get(String quoteId) {
        FxQuote quote = quotes.get(quoteId);
        if (quote == null) {
            return null;
        }
        if (clock.instant().isAfter(quote.getExpiresAt())) {
            quotes.remove(quoteId);
            return null;
        }
        return quote;
    }

    /** Housekeeping sweep; {@link #get} already treats expired quotes as absent. */
    @Scheduled(fixedDelay = 30_000)
    void evictExpired() {
        Instant now = clock.instant();
        quotes.values().removeIf(q -> now.isAfter(q.getExpiresAt()));
    }
}
