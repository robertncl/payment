package io.paylab.fx.rpc;

import com.alipay.sofa.runtime.api.annotation.SofaService;
import com.alipay.sofa.runtime.api.annotation.SofaServiceBinding;
import io.paylab.api.fx.FxFacade;
import io.paylab.api.fx.FxQuote;
import io.paylab.api.fx.LockQuoteRequest;
import io.paylab.fx.quote.QuoteService;
import org.springframework.stereotype.Component;

/** SOFARPC (bolt) endpoint for FX quotes; thin delegate to {@link QuoteService}. */
@Component
@SofaService(interfaceType = FxFacade.class, bindings = @SofaServiceBinding(bindingType = "bolt"))
public class FxFacadeImpl implements FxFacade {

    private final QuoteService quoteService;

    public FxFacadeImpl(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @Override
    public FxQuote lockQuote(LockQuoteRequest request) {
        return quoteService.lock(request);
    }

    @Override
    public FxQuote getQuote(String quoteId) {
        return quoteService.get(quoteId);
    }
}
