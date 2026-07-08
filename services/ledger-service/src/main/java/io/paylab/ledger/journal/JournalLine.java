package io.paylab.ledger.journal;

import java.math.BigDecimal;

/** One leg of a journal entry (value object used before insert; rows are append-only). */
public record JournalLine(String accountId, String currency, Direction direction, BigDecimal amount) {

    public enum Direction {
        DEBIT,
        CREDIT
    }

    public JournalLine {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("journal line amount must be positive: " + amount);
        }
        if (amount.scale() > 4) {
            throw new IllegalArgumentException("amount scale > 4: " + amount);
        }
    }

    /** Debit as positive, credit as negative — the convention balance checks sum over. */
    public BigDecimal signedAmount() {
        return direction == Direction.DEBIT ? amount : amount.negate();
    }

    /** Same account/currency/amount with the direction flipped; refunds are built from these. */
    public JournalLine reversed() {
        return new JournalLine(
                accountId, currency, direction == Direction.DEBIT ? Direction.CREDIT : Direction.DEBIT, amount);
    }
}
