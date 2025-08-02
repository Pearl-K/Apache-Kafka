package com.example.kafkapractice.producer;

import com.example.kafkapractice.domain.OrderEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderProducer {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private static final String TOPIC = "orders";

    public void send(OrderEvent event) {
        kafkaTemplate.send(TOPIC, event.orderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        System.err.println("Failed send: " + event.orderId() + " – " + ex.getMessage());
                        ex.printStackTrace();
                    } else {
                        System.out.println("Sent: " + event.orderId());
                    }
                });
    }
}