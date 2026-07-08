package io.paylab.ledger.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.paylab.ledger.journal.JournalLine.Direction;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class JournalLineTest {

    @Test
    void rejectsNullZeroNegativeAndOverScaledAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new JournalLine("a", "SGD", Direction.DEBIT, null));
        assertThrows(
                IllegalArgumentException.class, () -> new JournalLine("a", "SGD", Direction.DEBIT, BigDecimal.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JournalLine("a", "SGD", Direction.DEBIT, new BigDecimal("-1.0000")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JournalLine("a", "SGD", Direction.DEBIT, new BigDecimal("1.00001")));
    }

    @Test
    void signedAmountIsDebitPositiveCreditNegative() {
        BigDecimal ten = new BigDecimal("10.0000");
        assertEquals(ten, new JournalLine("a", "SGD", Direction.DEBIT, ten).signedAmount());
        assertEquals(ten.negate(), new JournalLine("a", "SGD", Direction.CREDIT, ten).signedAmount());
    }

    @Test
    void reversedFlipsDirectionAndKeepsEverythingElse() {
        JournalLine debit = new JournalLine("a", "SGD", Direction.DEBIT, new BigDecimal("10.0000"));
        JournalLine reversed = debit.reversed();

        assertEquals(Direction.CREDIT, reversed.direction());
        assertEquals(debit.accountId(), reversed.accountId());
        assertEquals(debit.currency(), reversed.currency());
        assertEquals(debit.amount(), reversed.amount());
        assertEquals(debit, reversed.reversed());
    }
}
