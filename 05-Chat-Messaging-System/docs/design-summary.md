# Chat Messaging System: Design Summary

## Problem Statement
Users exchange messages in real time, one-to-one and in groups. Messages must arrive quickly when the recipient is online, never be lost when they're offline, appear in the same order for everyone, and show the sender whether they were delivered and read.

## Functional Requirements
1. Send and receive 1:1 and group messages in real time.
2. Offline users get a push notification and receive missed messages when they reconnect.
3. Consistent message order within a conversation.
4. Delivery and read receipts (sent → delivered → read).
5. Message history per conversation.

## Non-Functional Requirements
- **Low latency:** < 200–300 ms end to end for online recipients.
- **Durability:** an acknowledged message is never lost.
- **Ordering:** per conversation (no global order needed).
- **Scale (assumed):** 50M daily active users, 40 messages/user/day → 2B messages/day; millions of concurrent connections.
- **Availability** over strict consistency for presence and receipts (they can lag briefly).

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **WebSocket gateways** keep a persistent connection per online device. They're stateful (they hold sockets) but contain no business logic.
- **Chat service** receives a message from a gateway, deduplicates by `clientMessageId`, assigns the next **sequence number** for the conversation, and appends to **Cassandra**.
- **Presence (Redis)** maps each online user to the gateway holding their socket.
- **Routing:** for each recipient, if online, publish to that gateway's pub/sub channel; the gateway pushes it down the socket and the device acks. If offline, the **push service** sends an APNs/FCM notification.
- **Reconnect sync:** the client sends its last delivered sequence per conversation; the server returns everything after it.

## API Design
WebSocket frames (JSON), plus REST for history:
```json
→ {"type":"send","conversationId":"trip","clientMessageId":"a-2","text":"Who's picking us up?"}
← {"type":"sent","clientMessageId":"a-2","sequence":2}
← {"type":"message","conversationId":"trip","sequence":2,"senderId":"alice","text":"..."}
→ {"type":"ack","conversationId":"trip","deliveredUpTo":2}
→ {"type":"read","conversationId":"trip","readUpTo":2}
```
```http
GET /conversations/trip/messages?after=0&limit=50     → history / sync
```

## Data Model
| Table | Partition key | Clustering | Columns |
|---|---|---|---|
| `messages` (Cassandra) | `conversation_id` | `sequence` ASC | `client_message_id`, `sender_id`, `text`, `sent_at` |
| `conversation_members` | `conversation_id` | `user_id` | `role`, `delivered_up_to`, `read_up_to` |
| `user_conversations` | `user_id` | `last_activity` DESC | `conversation_id` (inbox list) |
| Presence (Redis) | `presence:{user}` → gateway ID | TTL refreshed by heartbeats | |

Cassandra fits a write-heavy, append-only workload with "latest N messages of conversation X" reads that hit one partition. Very large group partitions can be bucketed by time (`conversation_id + month`).

## Key Design Decisions
| Decision | Why |
|---|---|
| **WebSockets** | Server can push instantly; polling wastes battery and adds latency. Long polling is the fallback. |
| **Per-conversation sequence numbers** | Total order within a conversation without global coordination; clients detect gaps and re-fetch. |
| **Client-generated message IDs** | Retries after a timeout don't create duplicates (idempotent send). |
| **Receipt cursors, not per-message flags** | "Read up to 57" is one write instead of 57; status is computed from cursors. |
| **Separate gateway and chat service** | Gateways scale with connections, the chat service scales with message rate. Deploys don't drop every socket. |
| **Push notification for offline users** | The message stays on the server; the push only wakes the app to sync. |

## Bottlenecks and Trade-offs
- **Millions of open sockets:** each gateway holds ~100k–1M connections (event-driven I/O). Scale horizontally; reconnect clients with backoff and jitter after a gateway restart to avoid a thundering herd.
- **Large groups:** fan-out to 1,000+ members per message is expensive. Cap group size, or have clients pull on notification for very large groups/channels.
- **Sequence assignment hotspot:** one conversation's sequence is serialized. Fine for chats (low per-conversation rate); shard by conversation.
- **Out-of-order delivery** across parallel fan-out: clients buffer and order by sequence, fetching missing ranges.
- **Presence accuracy vs cost:** heartbeat every 30–60 s with a TTL; presence is eventually consistent.

## Scaling Strategy
1. **Single server** holding sockets and storing messages (this repo's shape).
2. **Separate gateways from chat service;** add Redis presence and pub/sub routing between servers.
3. **Cassandra** for messages, partitioned by conversation; Kafka between chat service and fan-out for buffering.
4. **Regional gateways** close to users; replicate messages across regions asynchronously.
5. **Multi-device** support (cursor per device) and end-to-end encryption (server stores ciphertext only).
