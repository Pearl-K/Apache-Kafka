# Kafka의 메타데이터 관리 변화 (ZooKeeper → KRaft)
## 📌 Kafka 4.0 이전 구조 (ZooKeeper 기반)
Kafka는 3.x까지는 **분산 메타데이터 관리**를 위해 Apache ZooKeeper에 의존하고 있었다.


#### 구조 요약

```
Producer / Consumer
        ↓
Kafka Broker  <→ ZooKeeper (메타데이터 저장소)
             (controller 선출, topic 정보, ISR 관리)
```


* Kafka 클러스터가 부팅되면 ZooKeeper를 통해 **controller broker 선출**
* Topic 정보, 파티션, ISR, ACL 등 모든 메타데이터는 ZooKeeper에 저장
* Kafka는 ZooKeeper에서 메타데이터를 **읽어오거나 변경**만 수행

> 즉, **Kafka 자체가 클러스터의 메타정보를 직접 책임지지 않는 구조였다.**


---
## ❗ 이 구조의 문제점

| 문제점          | 설명                                                           |
| ------------ | ------------------------------------------------------------ |
| **복잡성**      | Kafka를 사용하려면 ZooKeeper라는 **별도 분산 시스템을 추가로 설치**해야함           |
| **운영 부담**    | ZooKeeper는 트래픽 최적화, GC 튜닝, 파일 스냅샷 등 **관리 포인트가 많음**           |
| **데이터 일관성**  | Kafka와 ZooKeeper 간의 **중간 상태(sync 문제)** 발생 가능                 |
| **개발 속도 제한** | Kafka의 새로운 기능 개발이 ZooKeeper 구조에 종속됨 (ex. controller 로직 변경 불가) |

---

## ✅ Kafka 4.0 이후: KRaft (Kafka Raft) 모드

Kafka 2.8.0부터 실험적으로 추가된 **KRaft 모드**가 Kafka 4.0에서 **기본/정식 모드**로 전환되었다.


### 핵심 변화: **ZooKeeper 완전 제거**
Kafka 클러스터 자체가 **Raft consensus 알고리즘 기반으로 메타데이터를 관리**한다.


#### 구조:

```
Producer / Consumer
        ↓
Kafka Broker (Raft 기반 metadata quorum)
```

* Kafka 자체가 **Controller 역할 + 메타데이터 저장소** 역할을 모두 수행한다.
* Raft quorum(3대 이상 권장)을 구성해 controller 선출과 복제를 Kafka 내부에서 수행한다.


---
## 🔍 이 변화가 왜 중요하고 의미 있는가?

### 1. 시스템 아키텍처가 단순해짐
* Kafka만 설치하면 된다. ZooKeeper는 더 이상 필요 없음
* 운영자는 **설정/배포/보안관리/모니터링 대상이 1개로 줄어든다.**


### 2. 클러스터 복구/확장/설정 변경이 쉬워짐
* Controller 전환 속도 빨라짐 → 리더 선출 장애 시간 감소
* Topic, ACL, Quota 변경도 Kafka CLI만으로 직접 가능


### 3. 성능 향상
* Kafka 브로커 간 통신만으로 metadata 처리 가능
* ZooKeeper round-trip latency 사라짐


### 4. 일관된 분산 알고리즘: Raft
* Raft는 Paxos 대비 **구현과 디버깅이 명확**한 합의 알고리즘이다.
* 모든 메타데이터 변경은 **Raft 로그에 기록 → commit → apply** 하는 구조이다.


---
## KRaft 구조 핵심 요약

| 구성 요소             | 설명                                                     |
| ----------------- | ------------------------------------------------------ |
| **Controller**    | 기존 ZooKeeper가 담당하던 역할 수행. Raft quorum에 참여              |
| **Metadata log**  | 모든 Kafka 설정과 상태는 Raft 로그에 commit되어 저장됨                 |
| **Quorum voters** | 최소 3대 이상 브로커가 `process.roles=controller` 설정으로 Raft 참여  |
| **Storage**       | `kafka-storage.sh format` 시 metadata 로그가 저장됨 (UUID 필요) |

---

## 운영자가 얻는 실질적인 이점
| 항목              | 설명                                                        |
| --------------- | --------------------------------------------------------- |
| **설치 간편**       | Kafka만 있으면 됨. ZooKeeper cluster 구성할 필요 없음                 |
| **운영 유지보수 감소**  | ZooKeeper-specific GC, log snapshot, chroot 등의 운영 포인트 사라짐 |
| **배포 자동화**      | Kafka 클러스터 구성 자동화가 쉬움 (Docker/K8s에서 더욱 효과적)               |
| **에러 원인 추적 쉬움** | 전체 동작이 Kafka 내부에서 통제되므로 디버깅 일관성 향상                        |


---
## ✅ 전환에 대한 결론
Kafka는 더 이상 ZooKeeper라는 외부 시스템에 의존하지 않고,
**Kafka 자체로 완전한 분산 시스템을 구성할 수 있게 되었다.**

> → 개발자 입장에선 구조가 단순해지고, 운영자 입장에선 관리할 대상이 줄었으며, 시스템 전체적으로는 일관성과 안정성이 올라간 구조적 진화로 볼 수 있다.



---

[(+ 번외) Kafka 4.0과 KRaft를 공부하면서 들었던 질문을 정리했습니다.](/chapter03/my-questions.md)
