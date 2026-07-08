package io.paylab.ledger.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.paylab.api.ledger.CapturePostingCommand;
import io.paylab.api.ledger.PostingResult;
import io.paylab.api.ledger.TrialBalanceReport;
import io.paylab.ledger.journal.JournalLine.Direction;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs the real Flyway schema against in-memory H2 in MySQL compatibility mode. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostingServiceTest {

    @Autowired
    private JdbcTemplate jdbc;

    private PostingService service() {
        return new PostingService(jdbc);
    }

    private static CapturePostingCommand capture(String paymentId) {
        CapturePostingCommand cmd = new CapturePostingCommand();
        cmd.setPaymentId(paymentId);
        cmd.setPayerId("payer-1");
        cmd.setMerchantId("merchant-1");
        cmd.setSourceCurrency("SGD");
        cmd.setTargetCurrency("MYR");
        cmd.setAmount(new BigDecimal("100.0000"));
        cmd.setFeeAmount(new BigDecimal("1.0000"));
        cmd.setFxRate(new BigDecimal("3.2259230769"));
        cmd.setTargetAmount(new BigDecimal("322.5923")); // round4(100 * rate)
        cmd.setFxQuoteId("quo_test");
        return cmd;
    }

    @Test
    void captureWritesFiveBalancedLegs() {
        PostingResult result = service().postCapture(capture("pay_1"));
        assertFalse(result.isAlreadyPosted());

        Integer lineCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_lines WHERE entry_id = ?", Integer.class, result.getEntryId());
        assertEquals(5, lineCount);

        TrialBalanceReport report = service().trialBalance();
        assertTrue(report.isBalanced(), "capture must net to zero per currency: " + report.getNetByCurrency());
        assertEquals(new BigDecimal("0.0000"), report.getNetByCurrency().get("SGD"));
        assertEquals(new BigDecimal("0.0000"), report.getNetByCurrency().get("MYR"));
    }

    @Test
    void captureReplayIsIdempotent() {
        PostingService service = service();
        PostingResult first = service.postCapture(capture("pay_2"));
        PostingResult replay = service.postCapture(capture("pay_2"));

        assertTrue(replay.isAlreadyPosted());
        assertEquals(first.getEntryId(), replay.getEntryId());
        Integer entries =
                jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE payment_id = 'pay_2'", Integer.class);
        assertEquals(1, entries);
    }

    @Test
    void refundReversesCaptureAndStaysBalanced() {
        PostingService service = service();
        service.postCapture(capture("pay_3"));
        PostingResult refund = service.postRefund("pay_3");
        assertFalse(refund.isAlreadyPosted());

        // replay refund -> idempotent
        assertTrue(service.postRefund("pay_3").isAlreadyPosted());

        TrialBalanceReport report = service.trialBalance();
        assertTrue(report.isBalanced());
        // after full reversal every account nets to zero
        report.getLines()
                .forEach(line -> assertEquals(
                        0,
                        line.getNet().compareTo(BigDecimal.ZERO),
                        line.getAccountId() + "/" + line.getCurrency() + " nets " + line.getNet()));
    }

    @Test
    void refundWithoutCaptureIsRejected() {
        assertThrows(IllegalStateException.class, () -> service().postRefund("pay_missing"));
    }

    @Test
    void mismatchedTargetAmountIsRejected() {
        CapturePostingCommand cmd = capture("pay_4");
        cmd.setTargetAmount(new BigDecimal("999.0000"));
        assertThrows(IllegalArgumentException.class, () -> service().postCapture(cmd));
    }

    @Test
    void zeroFeeCaptureOmitsFeeLegAndStaysBalanced() {
        CapturePostingCommand cmd = capture("pay_5");
        cmd.setFeeAmount(new BigDecimal("0.0000"));

        PostingResult result = service().postCapture(cmd);

        Integer lineCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_lines WHERE entry_id = ?", Integer.class, result.getEntryId());
        assertEquals(4, lineCount);
        assertTrue(service().trialBalance().isBalanced());
    }

    @Test
    void nonPositiveAmountIsRejected() {
        CapturePostingCommand zeroAmount = capture("pay_6");
        zeroAmount.setAmount(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> service().postCapture(zeroAmount));

        CapturePostingCommand nullAmount = capture("pay_6");
        nullAmount.setAmount(null);
        assertThrows(IllegalArgumentException.class, () -> service().postCapture(nullAmount));
    }

    @Test
    void negativeFeeIsRejected() {
        CapturePostingCommand cmd = capture("pay_7");
        cmd.setFeeAmount(new BigDecimal("-0.0001"));
        assertThrows(IllegalArgumentException.class, () -> service().postCapture(cmd));
    }

    @Test
    void zeroSumGuardCatchesUnbalancedLines() {
        List<JournalLine> bad = List.of(
                new JournalLine("a", "SGD", Direction.DEBIT, new BigDecimal("10.0000")),
                new JournalLine("b", "SGD", Direction.CREDIT, new BigDecimal("9.9999")));
        assertThrows(IllegalArgumentException.class, () -> PostingService.assertBalanced(bad));
    }
}
