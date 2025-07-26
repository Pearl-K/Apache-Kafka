# 로컬에서 카프카 테스트하기 (Kafka 4.0.0 + JDK17)

## 설치
- kafka 공식 버전 4.0.0 설치
- server.properties 옵션 추가 (쿼럼 관련 옵션)
- `kafka-server-start.bat` 으로 카프카 서버 실행하기


## 선행 단계
config/server.properties 에 아래 KRaft 설정 포함해야 정상 작동함

```powershell
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@localhost:9093
controller.quorum.bootstrap.servers=localhost:9093
listeners=PLAINTEXT://:9092,CONTROLLER://:9093
```


## Test
```powershell
# 1. Crate test-topic
PS C:\kafka\kafka_2.13-4.0.0> .\bin\windows\kafka-topics.bat --create --topic test-topic --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
2025-07-26T01:45:00.301287900Z main ERROR Reconfiguration failed: No configuration found for '66d3c617' at 'null' in 'null'
Created topic test-topic.

# 2. Producer에서 새로운 메시지 보내기, topic: test-topic
PS C:\kafka\kafka_2.13-4.0.0> .\bin\windows\kafka-console-producer.bat --topic test-topic --bootstrap-server localhost:9092
>hello kafka! for testing first message

# 3. Cosumer 새로 띄워서 메시지 소비하기, topic: test-topic
PS C:\kafka\kafka_2.13-4.0.0> .\bin\windows\kafka-console-consumer.bat --topic test-topic --from-beginning --bootstrap-server localhost:9092
hello kafka! for testing first message
Processed a total of 1 messages

# 4. 현재 띄워져 있는 kafka-topics의 리스트 출력해서 확인하기
PS C:\kafka\kafka_2.13-4.0.0> .\bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
__consumer_offsets
test-topic
```


아래는 카프카 클러스터 설정을 위한 단계이다.


```powershell
# 공통
process.roles=broker,controller
log.dirs=/tmp/kraft-logs-node-1

# 노드별 고유 설정
node.id=1

# voter 전체를 명시
controller.quorum.voters=1@localhost:9093,2@localhost:9095,3@localhost:9097

# listener 포트도 노드마다 다르게
listeners=PLAINTEXT://:9092,CONTROLLER://:9093
advertised.listeners=PLAINTEXT://localhost:9092,CONTROLLER://localhost:9093

controller.listener.names=CONTROLLER
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT

```
