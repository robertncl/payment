package io.paylab.risk.rules;

import io.paylab.api.risk.RiskAssessRequest;
import io.paylab.api.risk.RiskDecision;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule pipeline (spec §3): denylist → corridor cap → velocity, first hit declines. Every
 * decision is appended to risk_assessments, which is both the audit log and the data the
 * velocity rule counts. Idempotent per paymentId via unique index, so a gateway retry
 * replays the original decision instead of re-running rules against newer state.
 */
@Service
public class RiskCheckService {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int velocityMaxApproved;
    private final Duration velocityWindow;

    public RiskCheckService(
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${paylab.risk.velocity.max-approved:10}") int velocityMaxApproved,
            @Value("${paylab.risk.velocity.window-seconds:60}") long velocityWindowSeconds) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.velocityMaxApproved = velocityMaxApproved;
        this.velocityWindow = Duration.ofSeconds(velocityWindowSeconds);
    }

    @Transactional
    public RiskDecision assess(RiskAssessRequest request) {
        validate(request);
        Optional<RiskDecision> replay = findStored(request.getPaymentId());
        if (replay.isPresent()) {
            return replay.get();
        }
        RiskDecision decision = evaluate(request);
        return record(request, decision);
    }

    /** Runs the rule pipeline; does not persist anything. */
    private RiskDecision evaluate(RiskAssessRequest request) {
        if (isDenylisted("PAYER", request.getPayerId())) {
            return declined(RiskDecision.REASON_DENYLISTED_PAYER, "payer " + request.getPayerId());
        }
        if (isDenylisted("MERCHANT", request.getMerchantId())) {
            return declined(RiskDecision.REASON_DENYLISTED_MERCHANT, "merchant " + request.getMerchantId());
        }

        Optional<BigDecimal> cap = corridorCap(request.getSourceCurrency(), request.getTargetCurrency());
        if (cap.isEmpty()) {
            return declined(
                    RiskDecision.REASON_CORRIDOR_UNSUPPORTED,
                    request.getSourceCurrency() + "->" + request.getTargetCurrency());
        }
        if (request.getAmount().compareTo(cap.get()) > 0) {
            return declined(
                    RiskDecision.REASON_AMOUNT_OVER_CORRIDOR_CAP,
                    request.getAmount() + " exceeds corridor cap " + cap.get());
        }

        int recentApproved = approvedInWindow(request.getPayerId());
        if (recentApproved >= velocityMaxApproved) {
            return declined(
                    RiskDecision.REASON_VELOCITY_EXCEEDED,
                    recentApproved + " approved payments in the last " + velocityWindow.toSeconds() + "s");
        }

        return new RiskDecision(true, RiskDecision.REASON_APPROVED, null);
    }

    private boolean isDenylisted(String subjectType, String subjectId) {
        Integer hits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM denylist WHERE subject_type = ? AND subject_id = ?",
                Integer.class,
                subjectType,
                subjectId);
        return hits != null && hits > 0;
    }

    private Optional<BigDecimal> corridorCap(String sourceCurrency, String targetCurrency) {
        List<BigDecimal> caps = jdbc.queryForList(
                "SELECT max_amount FROM corridor_limits WHERE source_currency = ? AND target_currency = ?",
                BigDecimal.class,
                sourceCurrency,
                targetCurrency);
        return caps.stream().findFirst();
    }

    /** Approved decisions for this payer inside the rolling window (declines don't count). */
    private int approvedInWindow(String payerId) {
        Instant windowStart = clock.instant().minus(velocityWindow);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM risk_assessments WHERE payer_id = ? AND approved = 1 AND assessed_at >= ?",
                Integer.class,
                payerId,
                Timestamp.from(windowStart));
        return count == null ? 0 : count;
    }

    /**
     * Appends the decision; a concurrent duplicate for the same payment loses on the unique
     * index and returns the winner's stored decision instead.
     */
    private RiskDecision record(RiskAssessRequest request, RiskDecision decision) {
        try {
            jdbc.update(
                    """
                    INSERT INTO risk_assessments (payment_id, payer_id, merchant_id, source_currency,
                        target_currency, amount, approved, reason_code, detail, assessed_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """,
                    request.getPaymentId(),
                    request.getPayerId(),
                    request.getMerchantId(),
                    request.getSourceCurrency(),
                    request.getTargetCurrency(),
                    request.getAmount(),
                    decision.isApproved(),
                    decision.getReasonCode(),
                    decision.getDetail(),
                    Timestamp.from(clock.instant()));
            return decision;
        } catch (DuplicateKeyException raced) {
            return findStored(request.getPaymentId()).orElseThrow();
        }
    }

    private Optional<RiskDecision> findStored(String paymentId) {
        List<RiskDecision> stored = jdbc.query(
                "SELECT approved, reason_code, detail FROM risk_assessments WHERE payment_id = ?",
                (rs, i) -> new RiskDecision(
                        rs.getBoolean("approved"), rs.getString("reason_code"), rs.getString("detail")),
                paymentId);
        return stored.stream().findFirst();
    }

    private static RiskDecision declined(String reasonCode, String detail) {
        return new RiskDecision(false, reasonCode, detail);
    }

    private static void validate(RiskAssessRequest request) {
        if (request == null
                || request.getPaymentId() == null
                || request.getPayerId() == null
                || request.getMerchantId() == null
                || request.getAmount() == null) {
            throw new IllegalArgumentException("assess request is missing required fields");
        }
    }
}
