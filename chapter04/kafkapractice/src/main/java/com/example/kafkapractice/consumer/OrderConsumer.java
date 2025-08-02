package com.example.kafkapractice.consumer;

import com.example.kafkapractice.domain.OrderEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class OrderConsumer {

    @KafkaListener(topics = "orders", containerFactory = "kafkaListenerContainerFactory")
    public void listen(OrderEvent event, Acknowledgment ack) {
        try {
            System.out.println("Consumed: " + event.orderId() + " status=" + event.status());
            ack.acknowledge(); // 수동 커밋
        } catch (Exception e) {
            System.err.println("Processing failed: " + e.getMessage());

            // ... retry 로직 필요
        }
    }
}