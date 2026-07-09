package io.paylab.risk.rpc;

import com.alipay.sofa.runtime.api.annotation.SofaService;
import com.alipay.sofa.runtime.api.annotation.SofaServiceBinding;
import io.paylab.api.risk.RiskAssessRequest;
import io.paylab.api.risk.RiskDecision;
import io.paylab.api.risk.RiskFacade;
import io.paylab.risk.rules.RiskCheckService;
import org.springframework.stereotype.Component;

/** SOFARPC (bolt) endpoint for risk assessment; thin delegate to {@link RiskCheckService}. */
@Component
@SofaService(interfaceType = RiskFacade.class, bindings = @SofaServiceBinding(bindingType = "bolt"))
public class RiskFacadeImpl implements RiskFacade {

    private final RiskCheckService riskCheckService;

    public RiskFacadeImpl(RiskCheckService riskCheckService) {
        this.riskCheckService = riskCheckService;
    }

    @Override
    public RiskDecision assess(RiskAssessRequest request) {
        return riskCheckService.assess(request);
    }
}
