package io.paylab.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.paylab.api.fx.FxQuote;
import io.paylab.api.fx.LockQuoteRequest;
import io.paylab.api.ledger.CapturePostingCommand;
import io.paylab.api.risk.RiskAssessRequest;
import io.paylab.api.risk.RiskDecision;
import io.paylab.common.Money;
import io.paylab.gateway.chaos.ChaosFailureException;
import io.paylab.gateway.domain.Payment;
import io.paylab.gateway.domain.PaymentEvent;
import io.paylab.gateway.domain.PaymentStateMachine;
import io.paylab.gateway.domain.PaymentStatus;
import io.paylab.gateway.outbox.OutboxEvent;
import io.paylab.gateway.repo.OutboxRepository;
import io.paylab.gateway.repo.PaymentEventRepository;
import io.paylab.gateway.repo.PaymentRepository;
import io.paylab.gateway.rpc.RpcClients;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Payment lifecycle orchestration. Capture and refund run inside a Seata AT global
 * transaction (ADR-0004): the ledger posting and the local state commit are branches of one
 * XID, so a failure between them rolls both back. When Seata is disabled
 * (PAYLAB_SEATA_ENABLED=false — bare local runs), the same code degrades to the ADR-0002
 * ordering: idempotent RPC side effects BEFORE the local commit, healed by client retry.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentEventRepository events;
    private final OutboxRepository outbox;
    private final RpcClients rpc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;

    public PaymentService(
            PaymentRepository payments,
            PaymentEventRepository events,
            OutboxRepository outbox,
            RpcClients rpc,
            TransactionTemplate tx,
            ObjectMapper mapper) {
        this.payments = payments;
        this.events = events;
        this.outbox = outbox;
        this.rpc = rpc;
        this.tx = tx;
        this.mapper = mapper;
    }

    /**
     * Step 1 of the lifecycle: validates the request (supported ISO currencies, cross-border
     * only, positive DECIMAL(20,4) amount), asks risk-service for a verdict (denylist,
     * corridor cap, velocity — idempotent per paymentId, called before the local transaction
     * like every remote side effect), then in a single local transaction persists the payment
     * as RISK_APPROVED or RISK_DECLINED, records the transitions as {@link PaymentEvent}
     * rows, and enqueues a {@code payment.created} outbox event. The fee is fixed at creation
     * time by {@link FeePolicy}; no FX or ledger work happens yet — money only moves at
     * capture. RISK_DECLINED is terminal: the payment is returned, not an error.
     */
    public Payment create(
            String payerId, String merchantId, String sourceCurrency, String targetCurrency, BigDecimal amount) {
        Money.requireSupportedCurrency(sourceCurrency);
        Money.requireSupportedCurrency(targetCurrency);
        if (sourceCurrency.equals(targetCurrency)) {
            throw new IllegalArgumentException("source and target currency must differ (cross-border only)");
        }
        Money.requireValidAmount(amount);

        String paymentId = "pay_" + UUID.randomUUID();
        RiskDecision verdict = rpc.risk()
                .assess(new RiskAssessRequest(
                        paymentId, payerId, merchantId, sourceCurrency, targetCurrency, Money.round4(amount)));

        return tx.execute(status -> {
            Instant now = Instant.now();
            Payment payment = new Payment(
                    paymentId,
                    payerId,
                    merchantId,
                    sourceCurrency,
                    targetCurrency,
                    Money.round4(amount),
                    FeePolicy.feeFor(amount),
                    now);
            events.save(new PaymentEvent(payment.getId(), null, PaymentStatus.CREATED, null, now));

            // Transition BEFORE the first save: persist-then-modify in one tx loses the
            // status update when the IDENTITY event inserts force early execution of the
            // payment INSERT (observed on OceanBase; the events above keep the full trail).
            PaymentStatus outcome = verdict.isApproved() ? PaymentStatus.RISK_APPROVED : PaymentStatus.RISK_DECLINED;
            payment.transitionTo(outcome, now);
            events.save(new PaymentEvent(
                    payment.getId(),
                    PaymentStatus.CREATED,
                    outcome,
                    verdict.getReasonCode() + (verdict.getDetail() == null ? "" : ": " + verdict.getDetail()),
                    now));

            enqueue("payment.created", payment);
            return payments.save(payment);
        });
    }

    /**
     * Step 2: moves the money, inside one Seata AT global transaction (ADR-0004):
     * <ol>
     *   <li>Guard — reject the capture up front if the payment is not in a state that allows
     *       RISK_APPROVED → CAPTURED, before any remote side effect.</li>
     *   <li>FX — lock a 60s quote (captures always reference a locked quote, never a live
     *       rate) and compute the target amount from it. Quotes are in-memory and expire on
     *       their own, so fx is deliberately NOT a rollback branch.</li>
     *   <li>Ledger branch — post the double-entry capture over bolt; the XID travels with
     *       the call, so the journal insert enlists in the global transaction. Still
     *       idempotent per (paymentId, CAPTURE) as defense in depth.</li>
     *   <li>Local branch — re-read the payment inside a transaction (a concurrent capture
     *       may have won the race; if so return it as-is), transition to CAPTURED, attach
     *       the FX details, append the audit event, and enqueue {@code payment.captured}.</li>
     * </ol>
     * Any exception before the method returns — including the chaos hook that fires after
     * both branches completed — rolls back ledger and local state together. With Seata
     * disabled the same ordering degrades to ADR-0002 (idempotent callee first).
     *
     * @param chaosFailBeforeCommit forced-rollback gate: throw after both branches have done
     *     their work so the e2e can watch the global rollback undo them
     */
    @GlobalTransactional(name = "paylab-capture", rollbackFor = Exception.class)
    public Payment capture(String paymentId, boolean chaosFailBeforeCommit) {
        Payment payment = get(paymentId);
        // reject illegal transitions before any side effect
        PaymentStateMachine.assertTransition(payment.getStatus(), PaymentStatus.CAPTURED);

        // capture must reference a locked quote — never a live rate
        FxQuote quote = rpc.fx()
                .lockQuote(new LockQuoteRequest(
                        payment.getSourceCurrency(), payment.getTargetCurrency(), payment.getAmount()));
        BigDecimal targetAmount = Money.round4(payment.getAmount().multiply(quote.getRate()));

        CapturePostingCommand cmd = new CapturePostingCommand();
        cmd.setPaymentId(payment.getId());
        cmd.setPayerId(payment.getPayerId());
        cmd.setMerchantId(payment.getMerchantId());
        cmd.setSourceCurrency(payment.getSourceCurrency());
        cmd.setTargetCurrency(payment.getTargetCurrency());
        cmd.setAmount(payment.getAmount());
        cmd.setFeeAmount(payment.getFeeAmount());
        cmd.setFxRate(quote.getRate());
        cmd.setFxQuoteId(quote.getQuoteId());
        cmd.setTargetAmount(targetAmount);
        rpc.ledger().postCapture(cmd);

        Payment captured = tx.execute(status -> {
            Payment fresh = get(paymentId);
            if (fresh.getStatus() == PaymentStatus.CAPTURED) {
                return fresh; // lost a benign race; ledger side was idempotent
            }
            Instant now = Instant.now();
            fresh.transitionTo(PaymentStatus.CAPTURED, now);
            fresh.attachCapture(quote.getQuoteId(), quote.getRate(), targetAmount);
            events.save(new PaymentEvent(
                    fresh.getId(),
                    PaymentStatus.RISK_APPROVED,
                    PaymentStatus.CAPTURED,
                    "fx " + quote.getRate() + " (" + quote.getQuoteId() + ")",
                    now));
            enqueue("payment.captured", fresh);
            return payments.save(fresh);
        });

        if (chaosFailBeforeCommit) {
            throw new ChaosFailureException(
                    "chaos: forced failure after ledger + local branches for " + paymentId + " (global rollback)");
        }
        return captured;
    }

    /**
     * Full refund of a CAPTURED payment, in the same global-transaction shape as {@link
     * #capture}: the ledger posts the reversal as one branch (it re-derives the reversed legs
     * from the original CAPTURE entry, so no amounts are passed), the local CAPTURED →
     * REFUNDED transition is the other. Both sides stay idempotent as defense in depth.
     */
    @GlobalTransactional(name = "paylab-refund", rollbackFor = Exception.class)
    public Payment refund(String paymentId) {
        Payment payment = get(paymentId);
        PaymentStateMachine.assertTransition(payment.getStatus(), PaymentStatus.REFUNDED);

        rpc.ledger().postRefund(paymentId);

        return tx.execute(status -> {
            Payment fresh = get(paymentId);
            if (fresh.getStatus() == PaymentStatus.REFUNDED) {
                return fresh;
            }
            Instant now = Instant.now();
            fresh.transitionTo(PaymentStatus.REFUNDED, now);
            events.save(new PaymentEvent(
                    fresh.getId(),
                    PaymentStatus.CAPTURED,
                    PaymentStatus.REFUNDED,
                    "full refund; ledger reversal posted",
                    now));
            enqueue("payment.refunded", fresh);
            return payments.save(fresh);
        });
    }

    /** Loads a payment or translates absence into an HTTP 404. */
    public Payment get(String paymentId) {
        return payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "payment not found: " + paymentId));
    }

    /** Newest-first listing, optionally filtered by merchant; limit is clamped to [1, 200]. */
    public List<Payment> list(String merchantId, int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        return merchantId == null || merchantId.isBlank()
                ? payments.findAllByOrderByCreatedAtDesc(page)
                : payments.findByMerchantIdOrderByCreatedAtDesc(merchantId, page);
    }

    /** Full audit trail of status transitions for one payment, in the order they happened. */
    public List<PaymentEvent> timeline(String paymentId) {
        get(paymentId); // 404 if unknown
        return events.findByPaymentIdOrderByIdAsc(paymentId);
    }

    /**
     * Writes a domain event to the transactional outbox inside the caller's transaction, so
     * the event is published if and only if the state change commits. {@link
     * io.paylab.gateway.outbox.OutboxRelay} delivers it asynchronously.
     */
    private void enqueue(String eventType, Payment payment) {
        try {
            String payload = mapper.writeValueAsString(Map.of(
                    "paymentId", payment.getId(),
                    "status", payment.getStatus().name(),
                    "merchantId", payment.getMerchantId(),
                    "amount", payment.getAmount(),
                    "sourceCurrency", payment.getSourceCurrency(),
                    "targetCurrency", payment.getTargetCurrency()));
            outbox.save(new OutboxEvent("payment", payment.getId(), eventType, payload, Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize outbox payload", e);
        }
    }
}
