# Real-Time Chat Messaging System

WhatsApp-style real-time messaging, designed with scalable distributed system patterns, event-driven architecture, and observability tooling. The system supports multi-node WebSocket fan-out, Kafka-based persistence pipeline, offline message sync, delivery/read receipts, session replication through Redis, and fault-tolerant processing with DLQ.

---

##  Features
- Real-time messaging over WebSockets
- Distributed fan-out over Redis pub/sub
- Kafka event processing pipeline (`chat.messages`, `chat.read-receipts`, DLQ topics)
- Delivery & read receipts (`SENT → DELIVERED → READ`)
- Offline sync (chat summary + unread messages)
- Idempotent write handling using `clientMessageId`
- DLQ framework for bad event handling & replay
- Observability: Micrometer, Prometheus, Grafana dashboards
- Horizontal scalability + multi-node support

---

## Architecture Overview
```mermaid
flowchart LR
    Client -- WS/REST --> Node1[Chat Service Node 1]
    Client -- WS/REST --> Node2[Chat Service Node 2]

    Node1 <-- Redis Pub/Sub --> Node2

    Node1 --> Kafka[(Kafka topics)]
    Node2 --> Kafka

    Kafka --> PG[(PostgreSQL)]
    Kafka --> DLQ[(DLQ Topics)]
```

---

##  Tech Stack
| Component | Technology                      |
|-----------|---------------------------------|
| Language | Java 17                         |
| Framework | Spring Boot                     |
| Transport | WebSocket + REST                |
| Messaging | Apache Kafka                    |
| Fan-out | Redis Pub/Sub                   |
| Database | PostgreSQL                      |
| Observability | Micrometer, Prometheus, Grafana |
| Packaging | Maven / Docker                  |

---

##  Project Structure
```
backend/
  domain/
  ws/
  messaging/
  service/
  session/
  web/
  repository/
infra/
  docker-compose.yml
  prometheus.yml
```

---

##  API Documentation
### Users
| Method | Endpoint | Description |
|--------|-----------|-------------|
| POST | `/users` | Create user |

### Chats
| Method | Endpoint | Description |
|--------|-----------|-------------|
| POST | `/chats` | Create chat |
| GET | `/chats/summary` | Unread count per chat |

### Messages
| Method | Endpoint |
|--------|----------|
| POST `/messages/send` |
| POST `/messages/read` |
| GET `/messages?chatId&limit` |
| GET `/chats/{chatId}/messages/unread` |

### WebSocket Events
| Event |
|-------|
| `SEND_MESSAGE` |
| `MESSAGE_ACCEPTED` |
| `NEW_MESSAGE` |
| `MESSAGE_STATUS (DELIVERED)` |
| `READ_MESSAGES` |
| `READ_RECEIPT` |

---

## Running Locally
### Start infrastructure
```bash
cd infra
docker compose up -d
```

### Start chat service node-1
```bash
mvn clean package
java -jar target/chat-0.0.1-SNAPSHOT.jar --server.port=8080 --ws.node-id=node-1
```

### Optional node-2
```bash
java -jar target/chat-0.0.1-SNAPSHOT.jar --server.port=8081 --ws.node-id=node-2
```

---

## Observability & Metrics
### Key Application Metrics
```
chat_messages_processed_total
chat_messages_failed_total
chat_read_receipts_processed_total
chat_read_receipts_failed_total
chat_dlq_published_total
chat_ws_active_sessions
```

### Access endpoints
| Component | URL |
|-----------|-----|
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |
| Micrometer Metrics | http://localhost:8080/actuator/prometheus |

---

## Performance & NFR Targets
| Metric | Target | Status |
|--------|--------|--------|
| Message latency | p95 < 120ms | ~25-60ms locally |
| Receipt latency | < 80ms | ~15-35ms |
| Throughput | scalable via Kafka partitions | TBD load tests |
| WS sessions per node | 50K+ theoretical | 200 manually tested |

---

## Scalability
| Layer | Strategy |
|--------|----------|
| WebSocket | Horizontally scaled behind LB |
| Kafka | Increase partitions + consumer groups |
| Redis | Cluster mode / sharding |
| DB | Sharding + read replicas |
| Fault handling | DLQ + replay |
| Observability | metrics + alerts |

---

##  Roadmap
- Typing indicators
- Push notifications (offline routing)
- File uploads (S3 / MinIO)
- E2E encryption
- Search / indexing layer


---

## Architecture Diagrams (Mermaid)

### **System Architecture Diagram**
```mermaid
flowchart TB
    subgraph Clients
    A[Web Client] --- WS1[WebSocket]
    B[Mobile Client] --- WS2[WebSocket]
    end

    WS1 & WS2 --> LB[Load Balancer]
    LB --> N1[Chat Service Node-1]
    LB --> N2[Chat Service Node-2]

    N1 <-- Redis Pub/Sub --> N2

    N1 --> K1[(Kafka: chat.messages)]
    N2 --> K1

    K1 --> C1[Message Consumer]
    C1 --> DB[(PostgreSQL)]
    C1 --> RP[Redis Fan-out Publisher]

    RP --> Redis[Redis]
    Redis --> N1
    Redis --> N2
```

### **Message Send Flow (Sequence)**
```mermaid
sequenceDiagram
    participant U as User Client
    participant WS as WebSocket Gateway
    participant KP as Kafka Producer
    participant KC as Kafka Consumer
    participant DB as PostgreSQL
    participant RS as Redis Fanout

    U->>WS: SEND_MESSAGE(body, clientMessageId)
    WS-->>U: MESSAGE_ACCEPTED(status=SENT)
    WS->>KP: Publish ChatMessageEvent
    KP->>KC: Kafka deliver event
    KC->>DB: Persist message + receipts
    KC->>RS: Fanout notification
    RS->>WS: NEW_MESSAGE
    WS-->>U: NEW_MESSAGE
```

### **Read Receipt Flow**
```mermaid
sequenceDiagram
    participant U2 as Receiver Client
    participant WS as WebSocket
    participant KP as Kafka RR Producer
    participant KC as Kafka RR Consumer
    participant DB as PostgreSQL
    participant RS as Redis Fanout

    U2->>WS: READ_MESSAGES(messageIds)
    WS->>KP: Publish ReadReceiptEvent
    KP->>KC: Kafka delivery
    KC->>DB: Update read timestamps
    KC->>RS: Fanout read receipt event
    RS->>WS: READ_RECEIPT
```

---

##  Prometheus & Grafana Dashboard
### Example Dashboard Panels
- Message throughput: `sum(rate(chat_messages_processed_total[1m]))`
- Read receipt throughput: `sum(rate(chat_read_receipts_processed_total[1m]))`
- DLQ trends: `increase(chat_dlq_published_total[10m])`
- Active WS sessions per node: `chat_ws_active_sessions`

Add panels visually:
```
📍 Panel: Message Delivery Rate (/sec)
📍 Panel: DLQ Alerts
📍 Panel: Node-wise WS connections
📍 Panel: Consumer lag (Kafka)
```

---

## Load Testing & NFR Benchmarking
### Test Strategy
| Test | Description | Tools |
|-------|------------|--------|
| Message latency test | WS send→receive RTT p95 | custom script / k6 WS test |
| Throughput test | message/sec sustained on single node | JMeter/k6 |
| DLQ test | simulate failure under load | consumer exception injection |
| Multi-node fanout test | WS on node1, delivery from node2 | manual + load |

### Expected measurable outputs
| Metric | Target |
|--------|--------|
| p95 send→deliver latency | <120ms |
| read-receipt latency | <80ms |
| sustained throughput per node | 1k–10k msg/sec depending on partitions |
| WS sessions per node | 5k–50k depending on instance size |

---

##  Postman Collection
📎 Contains:
- REST API coverage
- WS test interactions
- Sample message flows with expected results

```
https://web.postman.co/workspace/My-Workspace~973e7165-970e-444b-876f-5af74d823fb4/collection/6921e9eac6a98eec4a83d39c?action=share&source=copy-link&creator=43067157

https://web.postman.co/workspace/My-Workspace~973e7165-970e-444b-876f-5af74d823fb4/collection/43067157-1e3aac63-7cf7-49aa-902b-850f080d22b4?action=share&source=copy-link&creator=43067157
```

