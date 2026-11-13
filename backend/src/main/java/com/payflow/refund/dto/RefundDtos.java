package com.payflow.refund.dto;

import com.payflow.refund.domain.Refund;
import com.payflow.refund.domain.RefundState;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public class RefundDtos {

    public record CreateRefundRequest(
            @NotBlank(message = "Payment ID is required")
            String paymentId,

            @NotNull(message = "Refund amount is required")
            @DecimalMin(value = "0.01", message = "Refund amount must be at least 0.01")
            BigDecimal amount,

            String reason
    ) {}

    public record RefundDto(
            String id,
            String paymentId,
            String merchantId,
            BigDecimal amount,
            String currency,
            String reason,
            RefundState status,
            String providerRefundId,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static RefundDto from(Refund refund) {
            return new RefundDto(
                    refund.getId(),
                    refund.getPaymentId(),
                    refund.getMerchantId(),
                    refund.getAmount(),
                    refund.getCurrency(),
                    refund.getReason(),
                    refund.getStatus(),
                    refund.getProviderRefundId(),
                    refund.getCreatedAt(),
                    refund.getUpdatedAt()
            );
        }
    }
}

