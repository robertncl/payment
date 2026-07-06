package io.paylab.api.ledger;

/**
 * Double-entry, append-only journal over SOFARPC/bolt. Corrections are reversing entries;
 * there is no update or delete operation by design.
 */
public interface LedgerFacade {

    /**
     * Posts the capture legs (all zero-sum per currency):
     *
     * <pre>
     * source ccy: DR payer_wallet (amount+fee) | CR fee_revenue (fee) | CR fx_pnl (amount)
     * target ccy: DR fx_pnl (targetAmount)     | CR settlement_clearing (targetAmount)
     * </pre>
     */
    PostingResult postCapture(CapturePostingCommand command);

    /** Posts reversing legs of the payment's CAPTURE entry. Idempotent per payment. */
    PostingResult postRefund(String paymentId);

    /** Recomputes account balances from the full journal. */
    TrialBalanceReport trialBalance();
}
