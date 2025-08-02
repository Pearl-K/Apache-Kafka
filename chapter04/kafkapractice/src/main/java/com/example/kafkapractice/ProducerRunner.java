package com.example.kafkapractice;

import com.example.kafkapractice.domain.OrderEvent;
import com.example.kafkapractice.producer.OrderProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ProducerRunner implements CommandLineRunner {

    private final OrderProducer producer;

    @Override
    public void run(String... args) {
        OrderEvent event = OrderEvent.of(
                "order-123",
                "CREATED",
                LocalDateTime.now()
        );
        producer.send(event);
    }
}
