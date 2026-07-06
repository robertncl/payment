package io.paylab.ledger.journal;

import io.paylab.api.ledger.CapturePostingCommand;
import io.paylab.api.ledger.PostingResult;
import io.paylab.api.ledger.TrialBalanceLine;
import io.paylab.api.ledger.TrialBalanceReport;
import io.paylab.ledger.journal.JournalLine.Direction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only posting engine. The only statements this class issues against journal tables
 * are INSERT and SELECT — the no-UPDATE/no-DELETE rule is structural, not conventional.
 */
@Service
public class PostingService {

    public static final String ENTRY_CAPTURE = "CAPTURE";
    public static final String ENTRY_REFUND = "REFUND";
    public static final String ENTRY_SETTLEMENT = "SETTLEMENT";

    private final JdbcTemplate jdbc;

    public PostingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every entry must sum to zero per currency — the double-entry invariant. */
    static void assertBalanced(List<JournalLine> lines) {
        Map<String, BigDecimal> netByCurrency = new HashMap<>();
        for (JournalLine line : lines) {
            netByCurrency.merge(line.currency(), line.signedAmount(), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> e : netByCurrency.entrySet()) {
            if (e.getValue().compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException("unbalanced posting: " + e.getKey() + " nets to " + e.getValue());
            }
        }
    }

    @Transactional
    public PostingResult postCapture(CapturePostingCommand cmd) {
        validateCapture(cmd);
        Optional<String> existing = findEntry(cmd.getPaymentId(), ENTRY_CAPTURE);
        if (existing.isPresent()) {
            return new PostingResult(existing.get(), true);
        }

        BigDecimal amount = cmd.getAmount();
        BigDecimal fee = cmd.getFeeAmount();
        BigDecimal targetAmount = cmd.getTargetAmount();
        String src = cmd.getSourceCurrency();
        String dst = cmd.getTargetCurrency();

        ensureAccount(Accounts.payerWallet(cmd.getPayerId()), Accounts.TYPE_PAYER_WALLET, cmd.getPayerId());
        ensureAccount(
                Accounts.merchantPayable(cmd.getMerchantId()), Accounts.TYPE_MERCHANT_PAYABLE, cmd.getMerchantId());
        ensureAccount(Accounts.FX_PNL, Accounts.TYPE_FX_PNL, null);
        ensureAccount(Accounts.FEE_REVENUE, Accounts.TYPE_FEE_REVENUE, null);
        ensureAccount(Accounts.SETTLEMENT_CLEARING, Accounts.TYPE_SETTLEMENT_CLEARING, null);

        List<JournalLine> lines = new ArrayList<>();
        // source currency legs
        lines.add(new JournalLine(Accounts.payerWallet(cmd.getPayerId()), src, Direction.DEBIT, amount.add(fee)));
        if (fee.signum() > 0) {
            lines.add(new JournalLine(Accounts.FEE_REVENUE, src, Direction.CREDIT, fee));
        }
        lines.add(new JournalLine(Accounts.FX_PNL, src, Direction.CREDIT, amount));
        // target currency legs
        lines.add(new JournalLine(Accounts.FX_PNL, dst, Direction.DEBIT, targetAmount));
        lines.add(new JournalLine(Accounts.SETTLEMENT_CLEARING, dst, Direction.CREDIT, targetAmount));

        assertBalanced(lines);
        return insertEntry(cmd.getPaymentId(), ENTRY_CAPTURE, cmd.getFxQuoteId(), lines);
    }

    @Transactional
    public PostingResult postRefund(String paymentId) {
        Optional<String> existingRefund = findEntry(paymentId, ENTRY_REFUND);
        if (existingRefund.isPresent()) {
            return new PostingResult(existingRefund.get(), true);
        }
        String captureEntryId = findEntry(paymentId, ENTRY_CAPTURE)
                .orElseThrow(() -> new IllegalStateException("no CAPTURE entry to refund for payment " + paymentId));

        List<JournalLine> reversed =
                loadLines(captureEntryId).stream().map(JournalLine::reversed).toList();
        assertBalanced(reversed);
        return insertEntry(paymentId, ENTRY_REFUND, null, reversed);
    }

    @Transactional(readOnly = true)
    public TrialBalanceReport trialBalance() {
        List<TrialBalanceLine> lines = jdbc.query(
                """
                SELECT l.account_id, a.type, l.currency,
                       SUM(CASE WHEN l.direction = 'DEBIT' THEN l.amount ELSE 0 END) AS debit_total,
                       SUM(CASE WHEN l.direction = 'CREDIT' THEN l.amount ELSE 0 END) AS credit_total
                FROM journal_lines l
                JOIN accounts a ON a.id = l.account_id
                GROUP BY l.account_id, a.type, l.currency
                ORDER BY l.account_id, l.currency
                """,
                (rs, i) -> {
                    BigDecimal debit = rs.getBigDecimal("debit_total").setScale(4, RoundingMode.UNNECESSARY);
                    BigDecimal credit = rs.getBigDecimal("credit_total").setScale(4, RoundingMode.UNNECESSARY);
                    return new TrialBalanceLine(
                            rs.getString("account_id"),
                            rs.getString("type"),
                            rs.getString("currency"),
                            debit,
                            credit,
                            debit.subtract(credit));
                });

        Map<String, BigDecimal> netByCurrency = new TreeMap<>();
        for (TrialBalanceLine line : lines) {
            netByCurrency.merge(line.getCurrency(), line.getNet(), BigDecimal::add);
        }
        boolean balanced = netByCurrency.values().stream().allMatch(net -> net.compareTo(BigDecimal.ZERO) == 0);
        return new TrialBalanceReport(Instant.now(), lines, netByCurrency, balanced);
    }

    private void validateCapture(CapturePostingCommand cmd) {
        if (cmd.getAmount() == null || cmd.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (cmd.getFeeAmount() == null || cmd.getFeeAmount().signum() < 0) {
            throw new IllegalArgumentException("fee must be >= 0");
        }
        BigDecimal expectedTarget = cmd.getAmount().multiply(cmd.getFxRate()).setScale(4, RoundingMode.HALF_EVEN);
        if (cmd.getTargetAmount() == null || expectedTarget.compareTo(cmd.getTargetAmount()) != 0) {
            throw new IllegalArgumentException("targetAmount " + cmd.getTargetAmount()
                    + " does not equal round4(amount * fxRate) = " + expectedTarget);
        }
    }

    private Optional<String> findEntry(String paymentId, String entryType) {
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM journal_entries WHERE payment_id = ? AND entry_type = ?",
                String.class,
                paymentId,
                entryType);
        return ids.stream().findFirst();
    }

    private List<JournalLine> loadLines(String entryId) {
        return jdbc.query(
                "SELECT account_id, currency, direction, amount FROM journal_lines WHERE entry_id = ? ORDER BY id",
                (rs, i) -> new JournalLine(
                        rs.getString("account_id"),
                        rs.getString("currency"),
                        Direction.valueOf(rs.getString("direction")),
                        rs.getBigDecimal("amount")),
                entryId);
    }

    private PostingResult insertEntry(String paymentId, String entryType, String fxQuoteId, List<JournalLine> lines) {
        String entryId = "ent_" + UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        try {
            jdbc.update(
                    "INSERT INTO journal_entries (id, payment_id, entry_type, fx_quote_id, created_at) VALUES (?,?,?,?,?)",
                    entryId,
                    paymentId,
                    entryType,
                    fxQuoteId,
                    now);
        } catch (DuplicateKeyException raced) {
            return new PostingResult(findEntry(paymentId, entryType).orElseThrow(), true);
        }
        for (JournalLine line : lines) {
            jdbc.update(
                    "INSERT INTO journal_lines (entry_id, account_id, currency, direction, amount, created_at) VALUES (?,?,?,?,?,?)",
                    entryId,
                    line.accountId(),
                    line.currency(),
                    line.direction().name(),
                    line.amount(),
                    now);
        }
        return new PostingResult(entryId, false);
    }

    private void ensureAccount(String id, String type, String ownerRef) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE id = ?", Integer.class, id);
        if (count != null && count == 0) {
            try {
                jdbc.update(
                        "INSERT INTO accounts (id, type, owner_ref, created_at) VALUES (?,?,?,?)",
                        id,
                        type,
                        ownerRef,
                        Timestamp.from(Instant.now()));
            } catch (DuplicateKeyException ignored) {
                // concurrent creation is fine
            }
        }
    }
}
