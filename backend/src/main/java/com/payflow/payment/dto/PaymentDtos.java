package com.payflow.payment.dto;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.domain.PaymentState;
import com.payflow.payment.domain.PaymentStateTransition;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class PaymentDtos {

    public record CreatePaymentRequest(
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
            BigDecimal amount,

            @NotBlank(message = "Currency is required")
            @Size(min = 3, max = 3, message = "Currency must be 3-letter ISO code")
            String currency,

            String customerEmail,
            String description,
            String paymentMethod
    ) {}

    public record PaymentDto(
            String id,
            String merchantId,
            BigDecimal amount,
            String currency,
            PaymentState status,
            String customerEmail,
            String description,
            String paymentMethod,
            String providerUsed,
            String providerReference,
            String failureReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static PaymentDto from(Payment payment) {
            return new PaymentDto(
                    payment.getId(),
                    payment.getMerchantId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getStatus(),
                    payment.getCustomerEmail(),
                    payment.getDescription(),
                    payment.getPaymentMethod(),
                    payment.getProviderUsed(),
                    payment.getProviderReference(),
                    payment.getFailureReason(),
                    payment.getCreatedAt(),
                    payment.getUpdatedAt()
            );
        }
    }

    public record PaymentDetailDto(
            PaymentDto payment,
            List<PaymentAttempt> attempts,
            List<PaymentStateTransition> transitions
    ) {}
}

