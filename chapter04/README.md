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
```

### 2. Producer 설정 (필요하면 명시적 설정)

```java

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
```

### 4. 실행용 Runner (애플리케이션 시작 후 테스트)

```java
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
```

---

## 4.5 컨슈머 애플리케이션 개발

### 1. Consumer 설정 (수동 커밋)

```java
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
```

---

## 4.6 핵심 개념 요약 (Producer / Consumer)

좋습니다. 더 정확하고 실무에 가까운 요약으로 정리하면 다음과 같습니다.

---

## **Producer**

* **KafkaTemplate / KafkaProducer 추상화**
  `KafkaTemplate`은 실제 Kafka 클라이언트인 `KafkaProducer`를 감싼 스프링 추상으로, 메시지 전송을 간결하게 처리하고 구성 가능한 콜백/리스너를 붙일 수 있게 한다.

* **신뢰성 설정**

  * `acks=all`: 모든 ISR(in-sync replica)으로부터 확인을 받아야 전송을 성공으로 간주하므로 데이터 손실 위험을 줄인다.
  * `retries`: 일시적 실패 시 재전송 시도. 재전송 시 중복 전송 가능성 때문에 `enable.idempotence=true`를 함께 쓰면 동일한 키/파티션에 대해 중복 없이 전송 보장이 강화된다 (exactly-once semantics에 가까워짐).
  * **Idempotence**: 프로듀서 측에서 중복 전송을 방지해 같은 메시지가 두 번 처리되는 것을 막는다.
  * **타임아웃 / 배치 / 지연**: `linger.ms`, `batch.size` 등으로 전송 효율과 지연 사이의 트레이드오프를 조절할 수 있다.

* **직렬화**

  * 키와 값은 각각 Serializer를 통해 바이트로 변환된다. 이 예제에서는 `String` 키는 `StringSerializer`, 값 객체는 `JsonSerializer`를 사용해 Jackson 기반 JSON으로 직렬화된다.
  * 복잡한 도메인 객체(예: record 또는 DTO)는 Jackson이 역직렬화할 수 있는 구조여야 하며, 타임타입(`LocalDateTime` 등)은 직렬화 포맷/모듈 등록이 올바른지 확인해야 한다.

* **전송 결과 처리**

  * 비동기 전송 결과는 `CompletableFuture` 또는 `ListenableFuture` API로 받는다. 성공/실패 콜백을 붙여 로깅, 재시도 정책, 알림 등을 구현한다.
    예: `whenComplete(...)`으로 성공과 실패를 분기 처리하거나, 전역적으로 `ProducerListener`를 설정해 모든 전송 결과를 관찰할 수 있다.

---

## **Consumer**

* **리스너 및 컨테이너 설정**

  * `@KafkaListener`와 `ConcurrentKafkaListenerContainerFactory`가 `KafkaConsumer`를 감싸며, 병렬 처리(스레드 수)를 `factory.setConcurrency(n)`으로 조정할 수 있다.
  * 컨슈머 그룹을 통해 같은 토픽을 여러 인스턴스가 나눠서 파티션 단위로 소비하여 확장성과 부하 분산을 확보한다.

* **오프셋 관리**

  * `enable.auto.commit=false`로 자동 커밋을 끄고, `Acknowledgment`를 통해 **명시적(수동) 커밋**을 제어한다.

    * 처리 로직이 완료된 시점에 `ack.acknowledge()`를 호출해야 해당 오프셋이 커밋되어 “정상 처리된 메시지”로 인정된다.
    * 이 방식은 **적어도 한 번(at-least-once)** 처리 보장을 제공하며, 실패 시 재처리 가능성을 남긴다.
    * \*\*정확히 한 번(exactly-once)\*\*이 필요하면 외부 상태와의 조정이나 트랜잭션 설계가 추가로 필요하다.

* **역직렬화**

  * 메시지 키/값은 각각 Deserializer를 통해 객체로 복원된다. `JsonDeserializer`를 쓰는 경우, 역직렬화 대상 클래스에 대해 신뢰할 패키지를 명시하는 `spring.json.trusted.packages` 설정이 필요하다.
  * 역직렬화 오류(포맷 깨짐, 예상 타입 불일치 등)는 처리 흐름을 깨트릴 수 있으므로 별도 예외 처리나 error handler를 둬야 한다.

* **오류 처리 전략**

  * 단순한 `try/catch`만으로는 poison message(계속 실패하는 메시지)나 무한 재시도가 발생할 수 있다.
  * 실무에서는 다음 같은 전략을 추가로 고려한다:

    * **재시도(backoff)**: 실패 시 일정한 지연을 두고 재시도.
    * **Dead Letter Queue(DLQ)**: 여러 번 실패한 메시지를 별도 “죽은 메시지” 토픽으로 보내고, 나중에 분석/재처리.
    * **정책 기반 실패 분리**: 특정 예외는 재시도, 특정 예외는 바로 DLQ 전송 등.

---

## **요약된 처리 보장 비교**

* **Producer 측**: `acks=all` + `retries` + `idempotence` 조합은 중복과 손실을 줄여 강한 전송 신뢰성을 준다.
* **Consumer 측**: 수동 커밋을 쓰면 처리 완료를 확실히 한 뒤 오프셋을 옮겨 “정확한 처리 제어”가 가능하지만, 실패하면 재처리될 수 있다는 점을 설계에 반영해야 한다.

