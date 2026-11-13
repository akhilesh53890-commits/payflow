package com.payflow.payment.domain;

import java.util.Set;

public enum PaymentState {
    CREATED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    UNKNOWN,
    PARTIALLY_REFUNDED,
    REFUNDED;

    public boolean canTransitionTo(PaymentState target) {
        return switch (this) {
            case CREATED -> Set.of(PROCESSING, CANCELLED).contains(target);
            case PROCESSING -> Set.of(SUCCEEDED, FAILED, UNKNOWN).contains(target);
            case UNKNOWN -> Set.of(SUCCEEDED, FAILED).contains(target);
            case SUCCEEDED -> Set.of(PARTIALLY_REFUNDED, REFUNDED).contains(target);
            case PARTIALLY_REFUNDED -> Set.of(PARTIALLY_REFUNDED, REFUNDED).contains(target);
            case FAILED, CANCELLED, REFUNDED -> false; // Terminal states
        };
    }
}

