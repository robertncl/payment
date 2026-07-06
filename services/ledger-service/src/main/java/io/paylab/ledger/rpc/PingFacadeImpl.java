package io.paylab.ledger.rpc;

import com.alipay.sofa.runtime.api.annotation.SofaService;
import com.alipay.sofa.runtime.api.annotation.SofaServiceBinding;
import io.paylab.api.PingFacade;
import org.springframework.stereotype.Component;

/** Phase 0 placeholder RPC service: proves this app registers a bolt provider in SOFARegistry. */
@Component
@SofaService(interfaceType = PingFacade.class, bindings = @SofaServiceBinding(bindingType = "bolt"))
public class PingFacadeImpl implements PingFacade {

    @Override
    public String ping(String from) {
        return "ledger-service:pong:" + from;
    }
}
