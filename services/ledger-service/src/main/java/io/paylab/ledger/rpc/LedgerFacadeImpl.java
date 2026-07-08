package io.paylab.ledger.rpc;

import com.alipay.sofa.runtime.api.annotation.SofaService;
import com.alipay.sofa.runtime.api.annotation.SofaServiceBinding;
import io.paylab.api.ledger.CapturePostingCommand;
import io.paylab.api.ledger.LedgerFacade;
import io.paylab.api.ledger.PostingResult;
import io.paylab.api.ledger.TrialBalanceReport;
import io.paylab.ledger.journal.PostingService;
import org.springframework.stereotype.Component;

/** SOFARPC (bolt) endpoint for ledger postings; thin delegate to {@link PostingService}. */
@Component
@SofaService(interfaceType = LedgerFacade.class, bindings = @SofaServiceBinding(bindingType = "bolt"))
public class LedgerFacadeImpl implements LedgerFacade {

    private final PostingService postingService;

    public LedgerFacadeImpl(PostingService postingService) {
        this.postingService = postingService;
    }

    @Override
    public PostingResult postCapture(CapturePostingCommand command) {
        return postingService.postCapture(command);
    }

    @Override
    public PostingResult postRefund(String paymentId) {
        return postingService.postRefund(paymentId);
    }

    @Override
    public TrialBalanceReport trialBalance() {
        return postingService.trialBalance();
    }
}
