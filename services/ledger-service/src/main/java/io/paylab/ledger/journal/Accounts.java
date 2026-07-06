package io.paylab.ledger.journal;

/** Chart of accounts (spec §2.4). House accounts are singletons; wallets/payables per owner. */
public final class Accounts {

    public static final String TYPE_PAYER_WALLET = "PAYER_WALLET";
    public static final String TYPE_MERCHANT_PAYABLE = "MERCHANT_PAYABLE";
    public static final String TYPE_FX_PNL = "FX_PNL";
    public static final String TYPE_FEE_REVENUE = "FEE_REVENUE";
    public static final String TYPE_SETTLEMENT_CLEARING = "SETTLEMENT_CLEARING";

    public static final String FX_PNL = "house:fx_pnl";
    public static final String FEE_REVENUE = "house:fee_revenue";
    public static final String SETTLEMENT_CLEARING = "house:settlement_clearing";

    public static String payerWallet(String payerId) {
        return "payer:" + payerId;
    }

    public static String merchantPayable(String merchantId) {
        return "merchant:" + merchantId;
    }

    private Accounts() {}
}
