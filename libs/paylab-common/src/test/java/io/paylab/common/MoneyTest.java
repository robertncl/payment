package io.paylab.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void round4UsesBankersRounding() {
        // exact ties round to the even neighbour, not always up
        assertEquals(new BigDecimal("1.0000"), Money.round4(new BigDecimal("1.00005")));
        assertEquals(new BigDecimal("1.0002"), Money.round4(new BigDecimal("1.00015")));
        assertEquals(new BigDecimal("2.3456"), Money.round4(new BigDecimal("2.34565")));
    }

    @Test
    void round4NormalizesScaleToFour() {
        assertEquals(new BigDecimal("7.0000"), Money.round4(new BigDecimal("7")));
        assertEquals(new BigDecimal("7.1000"), Money.round4(new BigDecimal("7.1")));
    }

    @Test
    void validAmountsPass() {
        assertDoesNotThrow(() -> Money.requireValidAmount(new BigDecimal("100")));
        assertDoesNotThrow(() -> Money.requireValidAmount(new BigDecimal("0.0001")));
        // 16 integer digits is the DECIMAL(20,4) ceiling
        assertDoesNotThrow(() -> Money.requireValidAmount(new BigDecimal("1234567890123456.0000")));
    }

    @Test
    void nullZeroAndNegativeAmountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Money.requireValidAmount(null));
        assertThrows(IllegalArgumentException.class, () -> Money.requireValidAmount(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Money.requireValidAmount(new BigDecimal("-1.0000")));
    }

    @Test
    void overScaledAndOversizedAmountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Money.requireValidAmount(new BigDecimal("1.00001")));
        assertThrows(
                IllegalArgumentException.class,
                () -> Money.requireValidAmount(new BigDecimal("12345678901234567.0000")));
    }

    @Test
    void supportedCurrenciesPassOthersAreRejected() {
        for (String currency : Money.SUPPORTED_CURRENCIES) {
            assertDoesNotThrow(() -> Money.requireSupportedCurrency(currency));
        }
        assertThrows(IllegalArgumentException.class, () -> Money.requireSupportedCurrency("THB"));
        assertThrows(IllegalArgumentException.class, () -> Money.requireSupportedCurrency("sgd"));
        assertThrows(IllegalArgumentException.class, () -> Money.requireSupportedCurrency(null));
    }
}
