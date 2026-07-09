package io.paylab.risk.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.paylab.api.risk.RiskAssessRequest;
import io.paylab.api.risk.RiskDecision;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs the real Flyway schema (with seed corridors/denylist) against in-memory H2. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RiskCheckServiceTest {

    private static final int VELOCITY_MAX = 3;
    private static final long WINDOW_SECONDS = 60;

    @Autowired
    private JdbcTemplate jdbc;

    /** Mutable clock so velocity-window expiry is tested without sleeping. */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-07-09T00:00:00Z");

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

    private final MutableClock clock = new MutableClock();

    private RiskCheckService service() {
        return new RiskCheckService(jdbc, clock, VELOCITY_MAX, WINDOW_SECONDS);
    }

    private static RiskAssessRequest request(String payer, String merchant, String amount) {
        return new RiskAssessRequest("pay_" + UUID.randomUUID(), payer, merchant, "SGD", "MYR", new BigDecimal(amount));
    }

    @Test
    void cleanRequestIsApproved() {
        RiskDecision decision = service().assess(request("payer-ok", "merchant-ok", "100.0000"));
        assertTrue(decision.isApproved());
        assertEquals(RiskDecision.REASON_APPROVED, decision.getReasonCode());
    }

    @Test
    void denylistedPayerIsDeclined() {
        RiskDecision decision = service().assess(request("payer-denylisted", "merchant-ok", "100.0000"));
        assertFalse(decision.isApproved());
        assertEquals(RiskDecision.REASON_DENYLISTED_PAYER, decision.getReasonCode());
    }

    @Test
    void denylistedMerchantIsDeclined() {
        RiskDecision decision = service().assess(request("payer-ok", "merchant-denylisted", "100.0000"));
        assertFalse(decision.isApproved());
        assertEquals(RiskDecision.REASON_DENYLISTED_MERCHANT, decision.getReasonCode());
    }

    @Test
    void unknownCorridorIsDeclined() {
        RiskAssessRequest thb = new RiskAssessRequest(
                "pay_" + UUID.randomUUID(), "payer-ok", "merchant-ok", "THB", "MYR", new BigDecimal("10.0000"));
        RiskDecision decision = service().assess(thb);
        assertFalse(decision.isApproved());
        assertEquals(RiskDecision.REASON_CORRIDOR_UNSUPPORTED, decision.getReasonCode());
    }

    @Test
    void amountOverCorridorCapIsDeclined() {
        RiskDecision atCap = service().assess(request("payer-cap", "merchant-ok", "10000.0000"));
        assertTrue(atCap.isApproved(), "cap itself is allowed");

        RiskDecision overCap = service().assess(request("payer-cap", "merchant-ok", "10000.0001"));
        assertFalse(overCap.isApproved());
        assertEquals(RiskDecision.REASON_AMOUNT_OVER_CORRIDOR_CAP, overCap.getReasonCode());
    }

    @Test
    void velocityBreachDeclinesUntilWindowSlides() {
        RiskCheckService service = service();
        for (int i = 0; i < VELOCITY_MAX; i++) {
            assertTrue(service.assess(request("payer-fast", "merchant-ok", "10.0000"))
                    .isApproved());
        }

        RiskDecision breach = service.assess(request("payer-fast", "merchant-ok", "10.0000"));
        assertFalse(breach.isApproved());
        assertEquals(RiskDecision.REASON_VELOCITY_EXCEEDED, breach.getReasonCode());

        // declines do not consume budget, and the window slides
        clock.now = clock.now.plusSeconds(WINDOW_SECONDS + 1);
        assertTrue(
                service.assess(request("payer-fast", "merchant-ok", "10.0000")).isApproved());
    }

    @Test
    void velocityIsPerPayer() {
        RiskCheckService service = service();
        for (int i = 0; i < VELOCITY_MAX; i++) {
            service.assess(request("payer-busy", "merchant-ok", "10.0000"));
        }
        assertTrue(
                service.assess(request("payer-quiet", "merchant-ok", "10.0000")).isApproved());
    }

    @Test
    void assessmentIsIdempotentPerPayment() {
        RiskCheckService service = service();
        RiskAssessRequest request = request("payer-idem", "merchant-ok", "10.0000");
        RiskDecision first = service.assess(request);

        // the same payment reassessed after a denylist hit still replays the original verdict
        jdbc.update(
                "INSERT INTO denylist (subject_type, subject_id, reason, created_at) VALUES ('PAYER','payer-idem','late hit', CURRENT_TIMESTAMP(6))");
        RiskDecision replay = service.assess(request);

        assertEquals(first.isApproved(), replay.isApproved());
        assertEquals(first.getReasonCode(), replay.getReasonCode());
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM risk_assessments WHERE payment_id = ?", Integer.class, request.getPaymentId());
        assertEquals(1, rows);
    }

    @Test
    void incompleteRequestIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service().assess(null));
        assertThrows(IllegalArgumentException.class, () -> service()
                .assess(new RiskAssessRequest(null, "p", "m", "SGD", "MYR", BigDecimal.ONE)));
    }
}
