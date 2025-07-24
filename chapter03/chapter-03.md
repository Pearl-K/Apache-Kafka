# 3장 카프카 설치
## 3.1 이 장의 내용
카프카 배포판인 컨플루언트 플랫폼을 이용한 카프카 클러스터 구축 방법을 소개한다.


카프카 클러스터는 1대 이상의 브로커로 구성되기 때문에, 서버를 1대만 사용하는 경우와 여러 대를 사용하는 경우에 대해 각각 구축하는 방법이 다르다.


> 4장 이후, 카프카 클러스터를 사용한 예제가 있다.


---
## 3.2 카프카 클러스터 환경 구축하기
### 3.2.1 클러스터 구성

카프카는 1대 이상의 브로커로 이루어진 카프카 클러스터, 프로듀서, 컨슈머와 카프카 클러스터를 실행하는 카프카 클라이언트로 구성된다.


카프카 클라이언트는 카프카 클러스터 상태 관리와 운영을 위한 다양한 조작에 필요하다.


아래 클러스터 구축을 각 단계별로 알아보고자 한다.
- 여러 대의 서버에서 카프카 클러스터를 구축하는 예시 (카프카 클러스터에 3대의 서버 이용하는 경우)
- 1대의 서버에 모두 설치하는 경우


구체적인 환경을 알아보기 전에, 모든 서버는 네트워크에 접속되어 있어 서로 통신이 가능하며 각자의 호스트명으로 해결할 수 있도록 해야한다.


### 3대의 서버로 카프카 클러스터 구축할 때 동작 환경


![img3-1](/chapter03/img/3-1.png)


![img3-2](/chapter03/img/3-2.png)


- 표와 그림의 예시 구조에서는 Producer와 Consumer가 각각 한 대씩 있는데, 이는 편의상의 구조이다.
- Producer/Consumer 가 처리량이나 원하는 구성에 따라서 더 많아질 수 있다.
- 사용하는 애플리케이션의 프레임워크 사양, 내장애성 등 여러 가지를 고려하여 유동적으로 여러 대를 사용하기도 한다.


### 1대의 서버만으로 카프카 클러스터 구축할 때 동작 환경


![img3-3](/chapter03/img/3-3.png)


- 이 구성은 브로커, 프로듀서, 컨슈머, 카프카 클라이언트를 모두 하나의 서버에 함께 설치한 구조이다.
- 이 구성은 본래 카프카가 갖는 규모나 내장애성의 구조를 살릴 수 없기 때문에, 테스트 환경이나 제약된 환경에서만 사용하는 것이 좋다.


### 3.2.2 각 서버의 소프트웨어 구성
- 카프카 클러스터는 1대 이상의 브로커와 주키퍼로 구성되어 있다.
- 이번에 구축할 카프카 클러스터는 브로커와 주키퍼를 동일 서버에 함께 설치하는 구성이다.
- 아래 그림의 구조를 참고하여, 구축할 환경의 서버에 설치해야하는 소프트웨어를 확인하자.


![img3-4](/chapter03/img/3-4.png)


- 여기에서는 클러스터를 구성하는 3대 모두에 카프카 브로커와 주키퍼를 설치한다.
- 주키퍼는 지속적인 서비스를 위해 항상 과반수가 동작하고 있어야 한다.
- 이러한 환경에서 주키퍼는 홀수의 노드 수가 바람직하다.


### 3.2.3 카프카 패키지와 배포판
- 다양한 개발사의 카프카 패키지와 배포판이 존재한다.
- 각 배포판들은 자체적으로 개발한 도구나, 다른 도구와의 연계를 위한 커스터마이징 기능을 제공한다.
- 따라서, 카프카를 Spark 같은 다른 미들웨어와 함께 사용할 경우, 이용 상황에 따라 적합한 배포판을 선택해야 한다.
- 배포판별로 출시 주기, update 등의 차이가 있으므로 잘 따져보아야 한다.
- 이 책에서는 기본값으로 컨플루언트가 배포하는 컨플루언트 플랫폼의 OSS 버전 카프카 클러스터를 구축할 예정이다.


---
## 3.3 카프카 구축 (Updated for Kafka 4.0 + JDK 21 기준)
기존 책에 설명된 내용은 과거 버전 기준이라 Zookeeper와 함께 동작하는 것을 기준으로 환경을 구축하고 있다.


그러나 최신버전에서는 Zookeeper와의 의존성을 피하고자 KRaft 컨트롤러를 사용한다. (아래 참고자료)


[Apache Kafka의 새로운 협의 프로토콜인 KRaft](https://devocean.sk.com/blog/techBoardDetail.do?ID=165737&boardType=techBlog)


### 3.3.1 OS 설치 (공통)

* **권장 OS**: Ubuntu 22.04 LTS 또는 24.04 LTS (CentOS 9 Stream, RHEL 10 등도 지원됨)
* **사전 조건**:

  ```bash
  sudo apt update && sudo apt upgrade -y
  sudo apt install curl wget tar net-tools unzip -y
  ```
* `hostname`, `hosts` 설정 확인
* 방화벽 설정 (Broker 간 통신: 기본 9092, ZooKeeper나 KRaft 포트 등)



### 3.3.2 JDK 설치

최신 LTS Java는 **JDK 21**이지만, Kafka 4.0 및 Confluent 8.0은 **JDK 17** / **21** 모두 지원한다.

* **설치 예시 (Ubuntu + Temurin 21)**:

  ```bash
  sudo apt install -y wget

  wget https://github.com/adoptium/temurin-21-jdk-latest-linux-x64.tar.gz

  sudo tar -xzvf temurin-21-jdk-latest-linux-x64.tar.gz -C /usr/lib/jvm/

  sudo update-alternatives --install /usr/bin/java java /usr/lib/jvm/…/bin/java 1

  sudo update-alternatives --install /usr/bin/javac javac /usr/lib/jvm/…/bin/javac 1
  ```
* **환경변수 설정**:

  ```bash
  export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk
  export PATH=$JAVA_HOME/bin:$PATH
  ```
* 설치 후 `java -version`, `javac -version`으로 확인

---

### 3.3.3 컨플루언트 플랫폼 레포지토리 등록 (공통)

Kafka만 직접 사용할 경우 생략 가능하다. 


Confluent Platform (Schema Registry, Control Center 등 포함)을 사용할 경우, 레포지토리 등록 필요:

```bash
wget -qO - https://packages.confluent.io/gpg.key | sudo apt-key add -
sudo add-apt-repository "deb [arch=amd64] https://packages.confluent.io/platform/8.0 stable main"
sudo apt update
```

* Confluent 8.0은 Kafka 4.0 기반으로, KRaft 모드 기본이다. 



### 3.3.4 카프카 설치 (공통)

#### A. 아파치 Kafka (ZooKeeper 없이 KRaft 모드로)

* Kafka 공식 다운로드 페이지에서 **Kafka 4.0 (Scala 2.13)** 선택
* 다운로드 예시:

  ```bash
  wget https://downloads.apache.org/kafka/4.0.0/kafka_2.13-4.0.0.tgz
  tar -xzf kafka_2.13-4.0.0.tgz
  mv kafka_2.13-4.0.0 /opt/kafka
  ```
* `server.properties` 수정: `process.roles=broker,controller`, `node.id`, `controller.quorum.voters`, `listeners`, `log.dirs` 설정


#### B. Confluent Platform

```bash
sudo apt install confluent-community-2.13
```

* 포함 구성 요소: Zookeeper 없이 Kafka 4.0 기반 설치




### 3.3.5 브로커의 데이터 디렉토리 설정 (공통)

* `server.properties`:

  ```properties
  log.dirs=/data/kafka/log
  ```
* 디렉토리 생성 및 권한 설정:

  ```bash
  sudo mkdir -p /data/kafka/log
  sudo chown -R kafka:kafka /data/kafka/log
  ```
* 추가 디렉토리 설정(JBOD 구성):

  ```properties
  log.dirs=/data/kafka/log1,/data/kafka/log2
  ```
* 필요 시 파일시스템 옵션 `noatime` 등 성능 튜닝



### 3.3.6 여러 대에서 동작하기 위한 설정

* **KRaft 모드**에서는 ZooKeeper가 필요 없으므로 Broker들이 자체 Quorum을 형성합니다.
* 각 노드 `server.properties` 예시:

  ```properties
  node.id=1                   # 각 노드 고유 ID
  process.roles=broker,controller
  controller.quorum.voters=1@host1:9093,2@host2:9093,3@host3:9093
  listeners=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
  inter.broker.listener.name=PLAINTEXT
  ```
* **Replication 및 Cluster 설정**:

  ```properties
  num.network.threads=3
  num.io.threads=8
  log.retention.hours=168
  default.replication.factor=3
  min.insync.replicas=2
  ```
* **동기화 설치**:

  * 모든 노드 동일 설정 배포
  * `bin/kafka-storage.sh random-uuid`로 **전용 storage** 초기화 (Kafka 4.0 이상)
  * `bin/kafka-storage.sh format -t <uuid> -c config/server.properties`
  * 예: `sudo -u kafka bin/kafka-server-start.sh config/server.properties`

---

### 📌 비교 테이블

| 항목           | 과거 책 기준 (JDK8 + ZooKeeper) | 최신 권장 구성 (Kafka 4.0 + JDK 21/17)                                                                                        |
| ------------ | -------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| **JDK 버전**   | 1.8 (지원 종료 임박)             | JDK 21 권장 (혹은 JDK 17) ([Apache Kafka][3], [Confluent Documentation][5], [Confluent Documentation][7], [bell-sw.com][8]) |
| **Kafka 버전** | 0.10 \~ 2.x                | 최신 4.0 (KRaft 모드 기본) ([Apache Kafka][6], [Confluent][4])                                                                |
| **메타데이터 관리** | ZooKeeper 의존               | KRaft 모드 – ZooKeeper 불필요                                                                                                |
| **데이터 디렉토리** | 단일 디렉토리, log.dirs 설정 가능    | JBOD, 다중 log.dirs 권장                                                                                                    |
| **클러스터 구성**  | ZooKeeper 연결 필요            | KRaft quorum 설정으로 완전 분산                                                                                                 |

---

### ✅ 정리

* Kafka 4.0 + KRaft 기반의 구축은 이전 버전에 비해 **운영 단순화**와 **확장성**을 제공한다.
* 최신 LTS JDK 21 활용 시 **보안과 성능** 모두 확보할 수 있다.


---
## 3.4 카프카 실행과 동작 확인

### 3.4.1 카프카 클러스터 실행

1. **Storage 포맷(최초 1회만)**

   ```bash
   # 각 노드에서 UUID 생성 및 포맷
   UUID=$(bin/kafka-storage.sh random-uuid)
   bin/kafka-storage.sh format -t $UUID -c config/server.properties
   ```
2. **브로커 시작**

   ```bash
   # foreground 실행 (테스트용)
   bin/kafka-server-start.sh config/server.properties

   # daemon 백그라운드 실행 (운영용 예시)
   nohup bin/kafka-server-start.sh config/server.properties \
     > /var/log/kafka/server.log 2>&1 &
   ```
3. **Systemd 유닛 사용 예시**
   `/etc/systemd/system/kafka.service`:

   ```ini
   [Unit]
   Description=Apache Kafka Broker
   After=network.target

   [Service]
   User=kafka
   ExecStart=/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/server.properties
   ExecStop=/opt/kafka/bin/kafka-server-stop.sh
   Restart=on-failure

   [Install]
   WantedBy=multi-user.target
   ```

   ```bash
   # 유닛 등록 및 시작
   sudo systemctl daemon-reload
   sudo systemctl enable kafka
   sudo systemctl start kafka
   ```

---

### 3.4.2 카프카 클러스터 동작 확인

1. **브로커 API 버전 확인**
   클러스터의 각 브로커가 정상적으로 올라왔는지 점검:

   ```bash
   bin/kafka-broker-api-versions.sh --bootstrap-server host1:9092,host2:9092
   ```
2. **토픽 생성·조회 테스트**

   ```bash
   # test 토픽 생성 (replication-factor=3, partitions=1)
   bin/kafka-topics.sh \
     --create \
     --bootstrap-server host1:9092 \
     --replication-factor 3 \
     --partitions 1 \
     --topic test-topic

   # 토픽 목록 확인
   bin/kafka-topics.sh --list --bootstrap-server host1:9092

   # 토픽 상세 정보 확인
   bin/kafka-topics.sh \
     --describe \
     --bootstrap-server host1:9092 \
     --topic test-topic
   ```
3. **메시지 송수신 테스트**

   * **Producer**:

     ```bash
     bin/kafka-console-producer.sh \
       --bootstrap-server host1:9092 \
       --topic test-topic
     # > hello kafka
     ```
   * **Consumer**:

     ```bash
     bin/kafka-console-consumer.sh \
       --bootstrap-server host1:9092 \
       --topic test-topic \
       --from-beginning \
       --timeout-ms 10000
     # hello kafka
     ```
4. **Controller 리더 확인**
   Controller 역할을 수행 중인 브로커를 확인:

   ```bash
   bin/kafka-shell.sh --bootstrap-server host1:9092 \
     --command-config config/admin.properties \
     --describe-quorum
   ```

   *(Confluent Control Center 사용 시 UI에서 클러스터 상태도 조회 가능)*

---

### 3.4.3 카프카 클러스터 종료

1. **스크립트를 통한 정상 종료**

   ```bash
   # 포그라운드로 기동했다면 Ctrl+C
   # 데몬으로 기동했다면:
   bin/kafka-server-stop.sh
   ```
2. **Systemd 유닛 사용 시**

   ```bash
   sudo systemctl stop kafka
   ```
3. **종료 확인**

   ```bash
   ps aux | grep kafka
   # kafka-server-start.sh 프로세스가 더 이상 보이지 않아야 정상 종료
   ```



---
## 3.5 정리
컨플루언트 플랫폼을 이용한 카프카 설치 방법(이전 버전)과 Kafka 4.0의 동작 차이가 있었다.

이 중, 4.0 버전으로 구축하고 동작을 확인했다. 다음 장에서는 이렇게 구축한 환경에서 카프카를 어떻게 사용하는지 살펴보겠다.