package io.paylab.fx.quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.paylab.api.fx.FxQuote;
import io.paylab.api.fx.LockQuoteRequest;
import io.paylab.fx.rates.StaticRateTable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class QuoteServiceTest {

    private final StaticRateTable table = new StaticRateTable();

    /** Mutable clock so TTL expiry is tested without sleeping. */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-07-07T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void locksDeterministicRateWithSpread() {
        MutableClock clock = new MutableClock();
        QuoteService service = new QuoteService(table, clock);

        FxQuote quote = service.lock(new LockQuoteRequest("SGD", "MYR", new BigDecimal("100.0000")));

        // mid = 4.2/1.3 = 3.23076923...; spread 15bps -> * 0.9985
        assertEquals(new BigDecimal("3.2259230769"), quote.getRate());
        assertEquals(clock.now.plus(Duration.ofSeconds(60)), quote.getExpiresAt());
        assertNotNull(quote.getQuoteId());
    }

    @Test
    void quoteRetrievableUntilTtlThenGone() {
        MutableClock clock = new MutableClock();
        QuoteService service = new QuoteService(table, clock);
        FxQuote quote = service.lock(new LockQuoteRequest("USD", "CNY", new BigDecimal("50")));

        clock.now = clock.now.plusSeconds(59);
        assertNotNull(service.get(quote.getQuoteId()));

        clock.now = clock.now.plusSeconds(2);
        assertNull(service.get(quote.getQuoteId()));
    }

    @Test
    void rejectsUnknownPairSameCurrencyAndNonPositiveAmounts() {
        QuoteService service = new QuoteService(table, new MutableClock());
        assertThrows(
                IllegalArgumentException.class, () -> service.lock(new LockQuoteRequest("SGD", "SGD", BigDecimal.ONE)));
        assertThrows(
                IllegalArgumentException.class, () -> service.lock(new LockQuoteRequest("THB", "MYR", BigDecimal.ONE)));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.lock(new LockQuoteRequest("SGD", "MYR", BigDecimal.ZERO)));
    }

    @Test
    void inversePairsAreNotReciprocal_becauseSpreadAppliesBothWays() {
        QuoteService service = new QuoteService(table, new MutableClock());
        BigDecimal sgdMyr =
                service.lock(new LockQuoteRequest("SGD", "MYR", BigDecimal.TEN)).getRate();
        BigDecimal myrSgd =
                service.lock(new LockQuoteRequest("MYR", "SGD", BigDecimal.TEN)).getRate();
        BigDecimal product = sgdMyr.multiply(myrSgd);
        // both legs pay the spread, so product < 1
        assertEquals(-1, product.compareTo(BigDecimal.ONE));
    }
}
