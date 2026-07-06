package io.paylab.gateway.web;

import com.alipay.common.tracer.core.context.trace.SofaTraceContext;
import com.alipay.common.tracer.core.holder.SofaTraceContextHolder;
import com.alipay.common.tracer.core.span.SofaTracerSpan;
import io.paylab.common.PayLab;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Every API response carries a trace id (spec hard rule). Runs innermost (lowest precedence)
 * so the SOFATracer servlet filter has already opened the span.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TraceIdResponseFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(PayLab.TRACE_ID_HEADER, currentTraceId());
        chain.doFilter(request, response);
    }

    static String currentTraceId() {
        try {
            SofaTraceContext context = SofaTraceContextHolder.getSofaTraceContext();
            SofaTracerSpan span = context.getCurrentSpan();
            if (span != null && span.getSofaTracerSpanContext() != null) {
                return span.getSofaTracerSpanContext().getTraceId();
            }
        } catch (Throwable tracerUnavailable) {
            // fall through to generated id
        }
        return "gen-" + UUID.randomUUID();
    }
}
