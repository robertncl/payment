package io.paylab.api.ledger;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class TrialBalanceReport implements Serializable {

    private static final long serialVersionUID = 1L;

    private Instant computedAt;
    private List<TrialBalanceLine> lines;
    /** Sum of nets per currency. The double-entry invariant requires every value to be 0.0000. */
    private Map<String, BigDecimal> netByCurrency;

    private boolean balanced;

    public TrialBalanceReport() {}

    public TrialBalanceReport(
            Instant computedAt, List<TrialBalanceLine> lines, Map<String, BigDecimal> netByCurrency, boolean balanced) {
        this.computedAt = computedAt;
        this.lines = lines;
        this.netByCurrency = netByCurrency;
        this.balanced = balanced;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }

    public List<TrialBalanceLine> getLines() {
        return lines;
    }

    public void setLines(List<TrialBalanceLine> lines) {
        this.lines = lines;
    }

    public Map<String, BigDecimal> getNetByCurrency() {
        return netByCurrency;
    }

    public void setNetByCurrency(Map<String, BigDecimal> netByCurrency) {
        this.netByCurrency = netByCurrency;
    }

    public boolean isBalanced() {
        return balanced;
    }

    public void setBalanced(boolean balanced) {
        this.balanced = balanced;
    }
}
