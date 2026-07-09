package io.paylab.gateway.web;

import com.alipay.sofa.rpc.core.exception.SofaRpcException;
import io.paylab.gateway.chaos.ChaosFailureException;
import io.paylab.gateway.domain.IllegalTransitionException;
import io.paylab.gateway.idempotency.IdempotencyService;
import io.paylab.gateway.web.PaymentDtos.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalTransitionException.class)
    public ResponseEntity<ApiError> illegalTransition(IllegalTransitionException e) {
        return error(HttpStatus.CONFLICT, "illegal_transition", e.getMessage());
    }

    @ExceptionHandler(IdempotencyService.ConflictException.class)
    public ResponseEntity<ApiError> idempotencyConflict(IdempotencyService.ConflictException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency_key_reuse", e.getMessage());
    }

    @ExceptionHandler(IdempotencyService.InFlightException.class)
    public ResponseEntity<ApiError> inFlight(IdempotencyService.InFlightException e) {
        return error(HttpStatus.CONFLICT, "request_in_flight", e.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> missingHeader(MissingRequestHeaderException e) {
        return error(HttpStatus.BAD_REQUEST, "missing_header", e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiError> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "invalid_request", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> illegalState(IllegalStateException e) {
        return error(HttpStatus.CONFLICT, "conflict", e.getMessage());
    }

    @ExceptionHandler(SofaRpcException.class)
    public ResponseEntity<ApiError> rpcFailure(SofaRpcException e) {
        return error(HttpStatus.BAD_GATEWAY, "downstream_unavailable", e.getMessage());
    }

    /** Forced-rollback gate: the injected fault surfaces as a plain 500 with a marker code. */
    @ExceptionHandler(ChaosFailureException.class)
    public ResponseEntity<ApiError> chaos(ChaosFailureException e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "chaos_injected", e.getMessage());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message, TraceIdResponseFilter.currentTraceId()));
    }
}
