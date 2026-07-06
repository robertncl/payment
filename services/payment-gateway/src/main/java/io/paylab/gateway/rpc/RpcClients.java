package io.paylab.gateway.rpc;

import com.alipay.sofa.runtime.api.annotation.SofaReference;
import com.alipay.sofa.runtime.api.annotation.SofaReferenceBinding;
import io.paylab.api.fx.FxFacade;
import io.paylab.api.ledger.LedgerFacade;
import org.springframework.stereotype.Component;

/** SOFARPC consumer references (bolt, resolved via SOFARegistry). */
@Component
public class RpcClients {

    @SofaReference(interfaceType = FxFacade.class, binding = @SofaReferenceBinding(bindingType = "bolt"))
    private FxFacade fxFacade;

    @SofaReference(interfaceType = LedgerFacade.class, binding = @SofaReferenceBinding(bindingType = "bolt"))
    private LedgerFacade ledgerFacade;

    public FxFacade fx() {
        return fxFacade;
    }

    public LedgerFacade ledger() {
        return ledgerFacade;
    }
}
