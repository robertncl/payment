package io.paylab.gateway.web;

import io.paylab.gateway.domain.Payment;
import io.paylab.gateway.domain.PaymentEvent;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {

    public record CreatePaymentRequest(
            @NotBlank String payerId,
            @NotBlank String merchantId,
            @NotBlank @Size(min = 3, max = 3) String sourceCurrency,
            @NotBlank @Size(min = 3, max = 3) String targetCurrency,

            @NotNull @Positive @Digits(integer = 16, fraction = 4)
            BigDecimal amount) {}

    public record PaymentResponse(
            String id,
            String status,
            String payerId,
            String merchantId,
            String sourceCurrency,
            String targetCurrency,
            BigDecimal amount,
            BigDecimal feeAmount,
            BigDecimal targetAmount,
            BigDecimal fxRate,
            String fxQuoteId,
            Instant createdAt,
            Instant updatedAt) {

        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(
                    p.getId(),
                    p.getStatus().name(),
                    p.getPayerId(),
                    p.getMerchantId(),
                    p.getSourceCurrency(),
                    p.getTargetCurrency(),
                    p.getAmount(),
                    p.getFeeAmount(),
                    p.getTargetAmount(),
                    p.getFxRate(),
                    p.getFxQuoteId(),
                    p.getCreatedAt(),
                    p.getUpdatedAt());
        }
    }

    public record PaymentEventResponse(String fromStatus, String toStatus, String detail, Instant occurredAt) {
        public static PaymentEventResponse from(PaymentEvent e) {
            return new PaymentEventResponse(e.getFromStatus(), e.getToStatus(), e.getDetail(), e.getOccurredAt());
        }
    }

    public record ApiError(String error, String message, String traceId) {}

    private PaymentDtos() {}
}
