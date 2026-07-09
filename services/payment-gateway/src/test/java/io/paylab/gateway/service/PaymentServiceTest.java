package io.paylab.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.paylab.api.fx.FxFacade;
import io.paylab.api.fx.FxQuote;
import io.paylab.api.ledger.CapturePostingCommand;
import io.paylab.api.ledger.LedgerFacade;
import io.paylab.api.ledger.PostingResult;
import io.paylab.api.risk.RiskDecision;
import io.paylab.api.risk.RiskFacade;
import io.paylab.gateway.chaos.ChaosFailureException;
import io.paylab.gateway.domain.IllegalTransitionException;
import io.paylab.gateway.domain.Payment;
import io.paylab.gateway.domain.PaymentEvent;
import io.paylab.gateway.domain.PaymentStatus;
import io.paylab.gateway.outbox.OutboxEvent;
import io.paylab.gateway.repo.OutboxRepository;
import io.paylab.gateway.repo.PaymentEventRepository;
import io.paylab.gateway.repo.PaymentRepository;
import io.paylab.gateway.rpc.RpcClients;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service-level tests with real repositories (H2 + Flyway schema) and mocked RPC facades, so
 * the branches e2e cannot reach deterministically — validation, guards before side effects,
 * and the benign lost-race paths — are pinned here.
 */
@DataJpaTest(properties = "spring.test.database.replace=NONE")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentServiceTest {

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private PaymentEventRepository events;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private PlatformTransactionManager txManager;

    private FxFacade fx;
    private LedgerFacade ledger;
    private RiskFacade risk;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        fx = mock(FxFacade.class);
        ledger = mock(LedgerFacade.class);
        risk = mock(RiskFacade.class);
        when(risk.assess(any())).thenReturn(new RiskDecision(true, RiskDecision.REASON_APPROVED, null));
        RpcClients rpc = mock(RpcClients.class);
        when(rpc.fx()).thenReturn(fx);
        when(rpc.ledger()).thenReturn(ledger);
        when(rpc.risk()).thenReturn(risk);
        service = new PaymentService(
                payments, events, outbox, rpc, new TransactionTemplate(txManager), new ObjectMapper());
    }

    private static FxQuote sgdMyrQuote() {
        Instant now = Instant.now();
        return new FxQuote("quo_1", "SGD", "MYR", new BigDecimal("3.2259230769"), now, now.plusSeconds(60));
    }

    private List<String> transitionsOf(String paymentId) {
        return events.findByPaymentIdOrderByIdAsc(paymentId).stream()
                .map(PaymentEvent::getToStatus)
                .toList();
    }

    private List<String> outboxEventTypes() {
        return outbox.findUnpublished(PageRequest.of(0, 100)).stream()
                .map(OutboxEvent::getEventType)
                .toList();
    }

    // ---------- create ----------

    @Test
    void createPersistsApprovedPaymentWithFeeEventsAndOutbox() {
        Payment payment = service.create("payer-1", "merchant-1", "SGD", "MYR", new BigDecimal("100.0000"));

        assertEquals(PaymentStatus.RISK_APPROVED, payment.getStatus());
        assertEquals(new BigDecimal("1.0000"), payment.getFeeAmount());
        assertEquals(List.of("CREATED", "RISK_APPROVED"), transitionsOf(payment.getId()));

        List<OutboxEvent> pending = outbox.findUnpublished(PageRequest.of(0, 10));
        assertEquals(1, pending.size());
        assertEquals("payment.created", pending.get(0).getEventType());
        assertEquals(payment.getId(), pending.get(0).getAggregateId());
        assertTrue(pending.get(0).getPayload().contains("RISK_APPROVED"));
    }

    @Test
    void createPersistsDeclinedPaymentWhenRiskSaysNo() {
        when(risk.assess(any()))
                .thenReturn(new RiskDecision(false, RiskDecision.REASON_DENYLISTED_PAYER, "payer payer-bad"));

        Payment payment = service.create("payer-bad", "merchant-1", "SGD", "MYR", new BigDecimal("100.0000"));

        assertEquals(PaymentStatus.RISK_DECLINED, payment.getStatus());
        assertEquals(List.of("CREATED", "RISK_DECLINED"), transitionsOf(payment.getId()));
        String reason =
                events.findByPaymentIdOrderByIdAsc(payment.getId()).get(1).getDetail();
        assertTrue(reason.contains(RiskDecision.REASON_DENYLISTED_PAYER), reason);
        // declined payments are terminal: capture must be rejected without side effects
        assertThrows(IllegalTransitionException.class, () -> service.capture(payment.getId(), false));
        verifyNoInteractions(fx, ledger);
    }

    @Test
    void createRejectsSameCurrencyCorridor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create("p", "m", "SGD", "SGD", new BigDecimal("10.0000")));
    }

    @Test
    void createRejectsUnsupportedCurrencyAndBadAmounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create("p", "m", "THB", "MYR", new BigDecimal("10.0000")));
        assertThrows(IllegalArgumentException.class, () -> service.create("p", "m", "SGD", "MYR", BigDecimal.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create("p", "m", "SGD", "MYR", new BigDecimal("1.00001")));
    }

    // ---------- capture ----------

    @Test
    void captureLocksQuotePostsLedgerAndTransitions() {
        when(fx.lockQuote(any())).thenReturn(sgdMyrQuote());
        when(ledger.postCapture(any())).thenReturn(new PostingResult("ent_1", false));
        Payment created = service.create("payer-1", "merchant-1", "SGD", "MYR", new BigDecimal("100.0000"));

        Payment captured = service.capture(created.getId(), false);

        assertEquals(PaymentStatus.CAPTURED, captured.getStatus());
        assertEquals("quo_1", captured.getFxQuoteId());
        assertEquals(new BigDecimal("322.5923"), captured.getTargetAmount());
        assertEquals(List.of("CREATED", "RISK_APPROVED", "CAPTURED"), transitionsOf(created.getId()));
        assertTrue(outboxEventTypes().contains("payment.captured"));

        ArgumentCaptor<CapturePostingCommand> cmd = ArgumentCaptor.forClass(CapturePostingCommand.class);
        verify(ledger).postCapture(cmd.capture());
        assertEquals(new BigDecimal("322.5923"), cmd.getValue().getTargetAmount());
        assertEquals(new BigDecimal("1.0000"), cmd.getValue().getFeeAmount());
        assertEquals("quo_1", cmd.getValue().getFxQuoteId());
    }

    @Test
    void captureRejectsIllegalStateBeforeAnySideEffect() {
        // persisted directly in CREATED: the risk step never approved it
        Payment stuck = payments.save(new Payment(
                "pay_stuck",
                "p",
                "m",
                "SGD",
                "MYR",
                new BigDecimal("10.0000"),
                new BigDecimal("0.1000"),
                Instant.now()));

        assertThrows(IllegalTransitionException.class, () -> service.capture(stuck.getId(), false));
        verifyNoInteractions(fx, ledger);
    }

    @Test
    void captureAbsorbsBenignRaceWithoutDoubleBooking() {
        when(fx.lockQuote(any())).thenReturn(sgdMyrQuote());
        Payment created = service.create("payer-1", "merchant-1", "SGD", "MYR", new BigDecimal("100.0000"));
        // a concurrent capture wins between the guard and the local commit
        when(ledger.postCapture(any())).thenAnswer(invocation -> {
            Payment winner = payments.findById(created.getId()).orElseThrow();
            winner.transitionTo(PaymentStatus.CAPTURED, Instant.now());
            payments.save(winner);
            return new PostingResult("ent_1", true);
        });

        Payment result = service.capture(created.getId(), false);

        assertEquals(PaymentStatus.CAPTURED, result.getStatus());
        // the loser must not append a second CAPTURED event or outbox row
        assertEquals(List.of("CREATED", "RISK_APPROVED"), transitionsOf(created.getId()));
        assertEquals(List.of("payment.created"), outboxEventTypes());
    }

    @Test
    void chaosFlagThrowsAfterBothBranches() {
        when(fx.lockQuote(any())).thenReturn(sgdMyrQuote());
        when(ledger.postCapture(any())).thenReturn(new PostingResult("ent_1", false));
        Payment created = service.create("payer-1", "merchant-1", "SGD", "MYR", new BigDecimal("100.0000"));

        // without Seata the branches stay committed — the rollback itself is the e2e's proof
        assertThrows(ChaosFailureException.class, () -> service.capture(created.getId(), true));
        verify(ledger).postCapture(any());
    }

    // ---------- refund ----------

    @Test
    void refundPostsReversalAndTransitions() {
        when(fx.lockQuote(any())).thenReturn(sgdMyrQuote());
        when(ledger.postCapture(any())).thenReturn(new PostingResult("ent_1", false));
        when(ledger.postRefund(anyString())).thenReturn(new PostingResult("ent_2", false));
        Payment created = service.create("payer-1", "merchant-1", "SGD", "MYR", new BigDecimal("100.0000"));
        service.capture(created.getId(), false);

        Payment refunded = service.refund(created.getId());

        assertEquals(PaymentStatus.REFUNDED, refunded.getStatus());
        verify(ledger).postRefund(created.getId());
        assertEquals(List.of("CREATED", "RISK_APPROVED", "CAPTURED", "REFUNDED"), transitionsOf(created.getId()));
        assertTrue(outboxEventTypes().contains("payment.refunded"));
    }

    @Test
    void refundRejectsUncapturedPaymentBeforeAnySideEffect() {
        Payment created = service.create("payer-1", "merchant-1", "SGD", "MYR", new BigDecimal("100.0000"));

        assertThrows(IllegalTransitionException.class, () -> service.refund(created.getId()));
        verifyNoInteractions(ledger);
    }

    @Test
    void refundAbsorbsBenignRaceWithoutDoubleBooking() {
        when(fx.lockQuote(any())).thenReturn(sgdMyrQuote());
        when(ledger.postCapture(any())).thenReturn(new PostingResult("ent_1", false));
        Payment created = service.create("payer-1", "merchant-1", "SGD", "MYR", new BigDecimal("100.0000"));
        service.capture(created.getId(), false);
        when(ledger.postRefund(anyString())).thenAnswer(invocation -> {
            Payment winner = payments.findById(created.getId()).orElseThrow();
            winner.transitionTo(PaymentStatus.REFUNDED, Instant.now());
            payments.save(winner);
            return new PostingResult("ent_2", true);
        });

        Payment result = service.refund(created.getId());

        assertEquals(PaymentStatus.REFUNDED, result.getStatus());
        assertEquals(List.of("CREATED", "RISK_APPROVED", "CAPTURED"), transitionsOf(created.getId()));
        assertEquals(List.of("payment.created", "payment.captured"), outboxEventTypes());
    }

    // ---------- reads ----------

    @Test
    void getAndTimelineTranslateUnknownIdTo404() {
        assertThrows(ResponseStatusException.class, () -> service.get("pay_missing"));
        assertThrows(ResponseStatusException.class, () -> service.timeline("pay_missing"));
    }

    @Test
    void listOrdersNewestFirstFiltersByMerchantAndClampsLimit() {
        Instant base = Instant.parse("2026-07-08T00:00:00Z");
        BigDecimal amount = new BigDecimal("10.0000");
        BigDecimal fee = new BigDecimal("0.1000");
        payments.save(new Payment("pay_a", "p", "m1", "SGD", "MYR", amount, fee, base));
        payments.save(new Payment("pay_b", "p", "m2", "SGD", "MYR", amount, fee, base.plusSeconds(1)));
        payments.save(new Payment("pay_c", "p", "m1", "SGD", "MYR", amount, fee, base.plusSeconds(2)));

        assertEquals(
                List.of("pay_c", "pay_b", "pay_a"),
                service.list(null, 50).stream().map(Payment::getId).toList());
        // blank merchant means no filter
        assertEquals(3, service.list(" ", 50).size());
        assertEquals(
                List.of("pay_c", "pay_a"),
                service.list("m1", 50).stream().map(Payment::getId).toList());
        // limit is clamped to at least 1 and at most 200
        assertEquals(
                List.of("pay_c"),
                service.list(null, 0).stream().map(Payment::getId).toList());
        assertEquals(3, service.list(null, 5000).size());
    }
}
