package io.paylab.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FeePolicyTest {

    @Test
    void feeIsOnePercentAtScaleFour() {
        assertEquals(new BigDecimal("1.0000"), FeePolicy.feeFor(new BigDecimal("100.0000")));
        assertEquals(new BigDecimal("0.5000"), FeePolicy.feeFor(new BigDecimal("50")));
    }

    @Test
    void feeRoundsHalfEven() {
        // 1% of 123.4567 = 1.234567 -> 1.2346
        assertEquals(new BigDecimal("1.2346"), FeePolicy.feeFor(new BigDecimal("123.4567")));
        // 1% of 0.0050 = 0.00005, a tie -> rounds to even 0.0000
        assertEquals(new BigDecimal("0.0000"), FeePolicy.feeFor(new BigDecimal("0.0050")));
    }

    @Test
    void tinyAmountsCanYieldZeroFee() {
        // the ledger must cope with a zero fee leg being omitted
        assertEquals(new BigDecimal("0.0000"), FeePolicy.feeFor(new BigDecimal("0.0001")));
    }
}
