package com.example.kafkapractice.domain;

import java.time.LocalDateTime;

public record OrderEvent(
        String orderId,
        String status,
        LocalDateTime timestamp
) {
    public static OrderEvent of(
            String orderId,
            String status,
            LocalDateTime timestamp
    ) {
        return new OrderEvent(orderId, status, timestamp);
    }
}

