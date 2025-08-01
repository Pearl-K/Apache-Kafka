# 4장 자바 API를 사용하여 애플리케이션 만들기

## 4.1 이 장의 내용
실습 목표

1. **Kafka 4.0 KRaft 모드**로 로컬 single-node 클러스터(=Zookeeper 없이) 띄우기
2. **Spring Boot (Gradle)** Java 애플리케이션을 만들기
   * **Producer**: `OrderEvent` 같은 메시지를 Kafka 토픽으로 전송
   * **Consumer**: 해당 토픽을 구독해서 메시지를 수신하고 처리
3. 핵심 개념 정리: 직렬화/역직렬화, 오프셋 커밋(수동), 신뢰성 옵션, 에러 핸들링


---
## 4.2 애플리케이션 개발 환경 준비
* Java 17 이상
* Gradle (Kotlin DSL)
* Kafka 4.0.x 바이너리 (KRaft 모드)


### Kafka 4.0 KRaft 모드로 로컬에 띄우기

```bash
# 작업 디렉토리 예: ~/kafka-4.0.0/

# 1. 로그/스토리지 디렉토리
mkdir -p /tmp/kraft-combined-logs

# 2. 클러스터 아이디 생성 (랜덤 UUID 출력)
bin/kafka-storage.sh random-uuid
# 예: 출력된 UUID를 복사: e.g. 5f9d2c3a-...

# 3. 메타데이터 초기화 (클러스터 아이디 넣어서)
bin/kafka-storage.sh format -t <복사한-UUID> -c config/kraft/server.properties

# 4. config/kraft/server.properties 수정
# server.properties 옵션 수정
# process.roles=broker,controller
# node.id=1
# controller.quorum.voters=1@localhost:9093
# listeners=PLAINTEXT://localhost:9092,CONTROLLER://localhost:9093
# advertised.listeners=PLAINTEXT://localhost:9092
# log.dirs=/tmp/kraft-combined-logs

# 5. Kafka 시작
bin/kafka-server-start.sh config/kraft/server.properties
```


> 토픽은 실습 전에 만들어두기!

```bash
bin/kafka-topics.sh --create --topic orders --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
```

---

## 4.2 애플리케이션: Gradle + Spring Boot 프로젝트 세팅

### `application.yml` 설정

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3

    consumer:
      group-id: sample-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
      auto-offset-reset: earliest
      enable-auto-commit: false
```

---

## 4.3 프로듀서 애플리케이션 개발

### 1. 메시지 모델 (`OrderEvent`) 예시

```java
package com.example.kafkapractice.domain;

public class OrderEvent {
    private String orderId;
    private String status;
    private long timestamp;

    public OrderEvent() {}

    public OrderEvent(String orderId, String status, long timestamp) {
        this.orderId = orderId;
        this.status = status;
        this.timestamp = timestamp;
    }

    // getters / setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
```

### 2. Producer 설정 (필요하면 명시적 설정)

```java
package com.example.kafkapractice.config;

import com.example.kafkapractice.domain.OrderEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.*;

import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, OrderEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, OrderEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

### 3. Producer 서비스

```java
package com.example.kafkapractice.producer;

import com.example.kafkapractice.domain.OrderEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderProducer {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private static final String TOPIC = "orders";

    public OrderProducer(KafkaTemplate<String, OrderEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(OrderEvent event) {
        kafkaTemplate.send(TOPIC, event.getOrderId(), event)
            .addCallback(
                success -> System.out.println("Sent: " + event.getOrderId()),
                failure -> System.err.println("Failed send: " + failure.getMessage())
            );
    }
}
```

### 4. 실행용 Runner (애플리케이션 시작 시 한 번 보내보기)

```java
package com.example.kafkapractice;

import com.example.kafkapractice.domain.OrderEvent;
import com.example.kafkapractice.producer.OrderProducer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ProducerRunner implements CommandLineRunner {

    private final OrderProducer producer;

    public ProducerRunner(OrderProducer producer) {
        this.producer = producer;
    }

    @Override
    public void run(String... args) {
        OrderEvent event = new OrderEvent("order-123", "CREATED", System.currentTimeMillis());
        producer.send(event);
    }
}
```

---

## 4.5 컨슈머 애플리케이션 개발

### 1. Consumer 설정 (수동 커밋)

```java
package com.example.kafkapractice.config;

import com.example.kafkapractice.domain.OrderEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, OrderEvent> consumerFactory() {
        JsonDeserializer<OrderEvent> deserializer = new JsonDeserializer<>(OrderEvent.class);
        deserializer.addTrustedPackages("*");
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "sample-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
```

### 2. Consumer 리스너

```java
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
            System.out.println("Consumed: " + event.getOrderId() + " status=" + event.getStatus());
            // 처리 끝나면 수동 커밋
            ack.acknowledge();
        } catch (Exception e) {
            System.err.println("Processing failed: " + e.getMessage());
            // 커밋하지 않아서 재처리 가능 (정책에 따라 DLQ 등이 필요)
        }
    }
}
```

---

## 4.6 핵심 개념 요약 (Producer / Consumer)

* **Producer**

  * `KafkaTemplate`은 내부적으로 `KafkaProducer`를 감싼 추상.
  * 신뢰성: `acks=all`, `retries`, callback으로 성공/실패 감지.
  * 직렬화: `JsonSerializer`로 객체 → JSON.

* **Consumer**

  * `@KafkaListener` + `ConcurrentKafkaListenerContainerFactory`로 감싼 `KafkaConsumer`.
  * 오프셋: `enable-auto-commit=false` + `Acknowledgment` 수동 커밋 → 정확한 처리 제어.
  * 역직렬화: `JsonDeserializer` + `trusted.packages` 세팅 필수.
  * 오류 시 재시도 / DLQ 전략을 별도 구현 가능.
  * 병렬 소비: `factory.setConcurrency(n)`으로 증가시킬 수 있음.

