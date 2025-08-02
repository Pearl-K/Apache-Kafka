```yaml
# Producer 준비 완료 (idempotent producer 생성)
  o.a.k.clients.producer.KafkaProducer     : [Producer clientId=producer-1] Instantiated an idempotent producer.

# 1. Producer가 이벤트 전송 시도
  Sent: order-123

# 2. Consumer가 그룹을 발견하고 조인 흐름
  org.apache.kafka.clients.Metadata        : [Consumer clientId=consumer-sample-group-1, groupId=sample-group] Cluster ID: kraft-cluster
  org.apache.kafka.clients.Metadata        : [Producer clientId=producer-1] Cluster ID: kraft-cluster
  o.a.k.c.c.internals.ConsumerCoordinator  : [Consumer clientId=consumer-sample-group-1, groupId=sample-group] Discovered group coordinator localhost:9092 (id: 2147483646 rack: null)
  o.a.k.c.c.internals.ConsumerCoordinator  : [Consumer clientId=consumer-sample-group-1, groupId=sample-group] (Re-)joining group
  o.a.k.c.c.internals.ConsumerCoordinator  : [Consumer clientId=consumer-sample-group-1, groupId=sample-group] Request joining group due to: need to re-join with the given member-id: consumer-sample-group-1-...
  o.a.k.c.c.internals.ConsumerCoordinator  : [Consumer clientId=consumer-sample-group-1, groupId=sample-group] (Re-)joining group
  o.a.k.c.c.internals.ConsumerCoordinator  : [Consumer clientId=consumer-sample-group-1, groupId=sample-group] Successfully joined group with generation Generation{generationId=1, memberId='consumer-sample-group-1-...', protocol='range'}
  o.a.k.c.c.internals.ConsumerCoordinator  : [Consumer clientId=consumer-sample-group-1, groupId=sample-group] Finished assignment for group at generation 1: {consumer-sample-group-1-...=Assignment(partitions=[orders-0])}
  o.s.k.l.KafkaMessageListenerContainer    : sample-group: partitions assigned: [orders-0]

# 3. 오프셋 초기화 (처음 읽는 메시지)
  o.a.k.c.c.internals.ConsumerCoordinator  : [Consumer clientId=consumer-sample-group-1, groupId=sample-group] Found no committed offset for partition orders-0
  o.a.k.c.c.internals.SubscriptionState    : [Consumer clientId=consumer-sample-group-1, groupId=sample-group] Resetting offset for partition orders-0 to position FetchPosition{offset=0, ...}

# 4. Consumer가 메시지 처리 완료
  Consumed: order-123 status=CREATED

```