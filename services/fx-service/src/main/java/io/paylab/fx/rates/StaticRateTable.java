package io.paylab.fx.rates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Static in-repo rate table (spec: MYR/SGD/USD/EUR/CNY, no live feed). Mids are quoted as
 * units of currency per 1 USD; cross rate S->T = mid(T)/mid(S), minus a fixed spread.
 * Deterministic on purpose so tests can assert exact numbers.
 */
@Component
public class StaticRateTable {

    public static final Set<String> CURRENCIES = Set.of("MYR", "SGD", "USD", "EUR", "CNY");

    /** Spread applied to the mid, in basis points. */
    public static final BigDecimal SPREAD_BPS = new BigDecimal("15");

    private static final Map<String, BigDecimal> USD_MID = Map.of(
            "USD", new BigDecimal("1.0000"),
            "MYR", new BigDecimal("4.2000"),
            "SGD", new BigDecimal("1.3000"),
            "EUR", new BigDecimal("0.9000"),
            "CNY", new BigDecimal("7.1000"));

    private static final int RATE_SCALE = 10;

    /** Whether the currency is one of the five lab corridor currencies. */
    public boolean supports(String currency) {
        return currency != null && CURRENCIES.contains(currency);
    }

    /** Client-side rate for converting source -> target (spread already deducted), scale 10. */
    public BigDecimal rate(String sourceCurrency, String targetCurrency) {
        if (!supports(sourceCurrency) || !supports(targetCurrency)) {
            throw new IllegalArgumentException("unsupported corridor " + sourceCurrency + "->" + targetCurrency);
        }
        if (sourceCurrency.equals(targetCurrency)) {
            throw new IllegalArgumentException("source and target currency must differ");
        }
        BigDecimal mid =
                USD_MID.get(targetCurrency).divide(USD_MID.get(sourceCurrency), RATE_SCALE + 4, RoundingMode.HALF_EVEN);
        BigDecimal spreadFactor =
                BigDecimal.ONE.subtract(SPREAD_BPS.divide(new BigDecimal("10000"), 8, RoundingMode.HALF_EVEN));
        return mid.multiply(spreadFactor).setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
    }
}
