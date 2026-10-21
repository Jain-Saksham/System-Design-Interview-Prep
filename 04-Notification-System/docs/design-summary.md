# Notification System: Design Summary

## Problem Statement
Many services need to notify users (order shipped, password reset, promotions) across email, SMS, and push. A central notification system gives them one API, handles unreliable third-party providers, respects user preferences, and makes sure users get each message once, not zero or five times.

## Functional Requirements
1. Accept notification requests from internal services: user, template, parameters, channels.
2. Deliver via email, SMS, and push through third-party providers.
3. Respect user preferences (opt-outs) and contact info.
4. Retry transient failures; give up and record after a limit.
5. Deduplicate retried requests (idempotency key).

## Non-Functional Requirements
- **Reliability:** at-least-once delivery to providers; no message lost after the API accepts it.
- **Low API latency:** callers get a response in milliseconds; delivery is asynchronous.
- **Scalability (assumed):** 10M notifications/day on average, bursts of 10× for marketing campaigns.
- **Isolation:** a slow SMS provider must not delay emails.
- **Compliance:** honour opt-outs; marketing vs transactional rules; quiet hours.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **Notification API** validates the request, checks the idempotency key (Redis `SET NX`), loads preferences and contact info, renders the template, and publishes one message per allowed channel.
- **Kafka topic per channel** buffers work. Bursts are absorbed, and each channel scales and fails independently.
- **Channel workers** consume, call the provider, and on failure re-publish to a retry topic with exponential backoff. After max attempts the message goes to a **dead-letter queue** for alerting and inspection.
- **Providers** (SES, Twilio, APNs/FCM) do final delivery. Their webhooks can report delivery status.

## API Design
```http
POST /notifications
{ "idempotencyKey": "order-981-shipped", "userId": "u42", "templateId": "order-shipped",
  "params": { "name": "Asha", "orderId": "#981" }, "channels": ["EMAIL", "SMS", "PUSH"] }

202 Accepted
{ "notificationId": "7c1e...", "duplicate": false, "queued": ["EMAIL", "SMS"], "skipped": ["PUSH"] }
```
`202` because delivery happens later. A repeated `idempotencyKey` returns the original `notificationId` with `"duplicate": true`.

## Data Model
| Entity | Key fields | Store |
|---|---|---|
| User preferences | `user_id`, `email`, `phone`, `device_tokens[]`, `opted_out_channels[]`, `quiet_hours` | PostgreSQL + cache |
| Template | `template_id`, `channel`, `locale`, `body` | PostgreSQL (rarely changes, heavily cached) |
| Idempotency key | `key → notification_id`, TTL 24h | Redis |
| Delivery log | `notification_id`, `channel`, `status`, `attempts`, `provider_message_id`, timestamps | Cassandra / DynamoDB (high write volume, queried by notification or user) |

## Key Design Decisions
| Decision | Why |
|---|---|
| **Async via queues** | Providers are slow and flaky; callers shouldn't wait. Queues absorb campaign bursts. |
| **One topic per channel** | Isolation: an SMS outage doesn't block email, and each channel scales to its own provider limits. |
| **Exponential backoff** | Retrying immediately hammers a struggling provider; backoff gives it room. Add jitter so retries don't synchronize. |
| **Dead-letter queue** | Poison messages (bad phone number) stop retrying, and humans can inspect them. |
| **Idempotency key from the caller** | Callers retry on timeouts; without a key, users get duplicates. |
| **Preferences checked at send time** | A user who just opted out must not receive queued marketing. Workers can re-check for delayed messages. |

## Bottlenecks and Trade-offs
- **At-least-once means possible duplicates:** a worker can crash after the provider accepted the message but before committing its offset. Mitigate by passing an idempotency key to providers that support it and deduplicating by `notification_id + channel` in workers.
- **Provider rate limits:** throttle workers per provider (token bucket) and spread campaigns over time.
- **Marketing bursts starving transactional messages:** use separate topics or priorities so password resets aren't stuck behind a 5M-user campaign.
- **Hot users or spam:** cap notifications per user per hour, and collapse similar notifications into digests.
- **Provider outage:** fail over to a secondary provider per channel.

## Scaling Strategy
1. **Single service + in-process queue** (this repo's shape): fine for low volume.
2. **Move the queue to Kafka/SQS** and run workers separately, which gives durability and independent scaling.
3. **Partition topics by user ID** to keep per-user ordering and scale consumers horizontally.
4. **Priority lanes** (transactional vs marketing) and per-provider throttling.
5. **Multi-provider and multi-region:** fail over between providers; send from the region closest to the user.
