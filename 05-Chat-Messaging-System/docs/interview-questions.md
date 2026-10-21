# Chat Messaging System: Interview Questions

**1. Estimate the message volume and storage.**
Assume 50M DAU × 40 messages/day = **2B messages/day** ≈ 23k/s average (peaks maybe 3×). At ~200 bytes per message with metadata: 2B × 200 B ≈ **400 GB/day**, ~146 TB/year before replication. That points to a horizontally scalable, write-optimized store.

**2. WebSockets vs long polling vs server-sent events?**
WebSockets are full-duplex and persistent, which suits chat because both sides send frequently. Long polling works everywhere but has higher overhead and latency. SSE is server→client only. Use WebSockets with long polling as a fallback.

**3. How does a message reach a recipient connected to a different server?**
The chat service looks up the recipient's gateway in the Redis presence map and publishes to that gateway's channel (Redis pub/sub or Kafka). The gateway writes the frame to the recipient's socket.

**4. How do you guarantee message ordering?**
Per conversation, the server assigns a monotonically increasing sequence number at write time (serialized per conversation). Clients display by sequence and fetch any gaps. A global order isn't needed and would be a bottleneck. Client timestamps are unreliable due to clock skew.

**5. The sender's network drops after sending. How do you avoid duplicates?**
The client generates a `clientMessageId` and retries with the same ID. The server deduplicates on `(conversation_id, client_message_id)` and returns the original sequence. That's an idempotent send.

**6. How does an offline user get their messages?**
The message is persisted. The push service sends an APNs/FCM notification. When the app reconnects, it sends its last delivered sequence per conversation, and the server returns everything after it.

**7. How do delivery and read receipts work efficiently?**
Each member has `delivered_up_to` and `read_up_to` cursors per conversation. One ack updates the cursor. The sender's status for message N is READ if every other member's read cursor ≥ N, DELIVERED if every delivered cursor ≥ N, else SENT. Receipts are pushed to the sender like regular messages.

**8. Why Cassandra for messages?**
Very high write throughput, linear horizontal scaling, and the dominant query ("latest messages of conversation X" or "after sequence N") maps to one partition with a clustering order. Relational DBs work at smaller scale but sharding them is harder.

**9. How do you handle group chats with thousands of members?**
Write the message once and fan out references, not copies. For very large groups, don't push every message: send a lightweight "new activity" signal and let clients pull. Cap group sizes, and treat broadcast channels as a different product (pull-based, like a feed).

**10. How do you track presence (online/last seen)?**
Gateways set `presence:{user}` in Redis with a TTL on connect and refresh it with heartbeats. Disconnect or TTL expiry means offline. Presence is fanned out lazily (only to users who have the chat open) to avoid a storm of updates.

**11. A gateway server crashes. What happens?**
Its sockets drop and clients reconnect (with jittered backoff) to another gateway through the load balancer. Presence entries expire or are overwritten. Nothing is lost: undelivered messages are in the store, and clients sync from their last delivered sequence.

**12. How would you add end-to-end encryption?**
Clients encrypt with keys the server never sees (Signal protocol). The server stores and routes ciphertext only. Server-side search and moderation become impossible, and group key management gets more complex.

**Likely follow-ups:** multi-device sync (cursor per device) · media messages (upload to object storage, send a URL) · message deletion and editing · search over history (client-side, if E2E encrypted).
