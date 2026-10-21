# Notification System: Interview Questions

**1. Estimate the throughput.**
Assume 10M notifications/day: 10M / 86,400 ≈ **115/s** on average. Marketing campaigns burst to maybe 10× (~1,200/s) or more. The queue absorbs bursts, so workers are sized for sustained throughput and provider rate limits, not peak arrival rate.

**2. Why make delivery asynchronous?**
Providers take hundreds of milliseconds and fail intermittently. A synchronous API would make every caller slow and fragile. The API accepts the request, persists it to a queue, and returns `202`; workers deliver in the background.

**3. Why a separate queue per channel?**
Isolation and independent scaling. If Twilio is down, SMS messages pile up in their own topic while email keeps flowing. Each channel's worker pool is sized to that provider's rate limit.

**4. What delivery guarantee do you provide?**
At-least-once: a message is retried until the provider acknowledges it, and offsets are committed only after a successful send. Exactly-once isn't possible end to end with external providers, so we deduplicate where we can (idempotency keys, provider message IDs).

**5. How do you prevent duplicate notifications?**
Two layers. (a) **Caller retries:** the request carries an idempotency key; the API does `SET key NX EX 86400` in Redis and returns the original result if the key exists. (b) **Worker retries:** record `notification_id + channel` as sent, and check before sending again.

**6. How does retry with backoff work?**
On a transient failure, re-publish with `attempt + 1` and a delay of `base × 2^(attempt−1)` plus random jitter (e.g., 1 s, 2 s, 4 s, 8 s). Kafka has no native delay, so use retry topics with fixed delays (`retry-1s`, `retry-30s`) or a delay queue (SQS). After max attempts → dead-letter queue + alert.

**7. Which failures should NOT be retried?**
Permanent ones: invalid phone number, unsubscribed email, push token unregistered (APNs 410). Mark them failed immediately and update the user's contact info (e.g., remove the dead device token).

**8. How do you handle user preferences and opt-outs?**
Check them in the API before queuing, and re-check in workers for delayed or scheduled messages. Legally, marketing must honour opt-outs; transactional messages (password reset) usually bypass marketing opt-outs but still need a valid channel.

**9. A 5M-user marketing campaign is delaying password-reset emails. Fix it.**
Use separate priority lanes: transactional topics with dedicated workers, and marketing topics throttled to leave provider capacity. Spread campaigns over time windows.

**10. How do you track whether a notification was delivered or opened?**
Store a delivery log (status per channel). Providers call webhooks (delivered, bounced, opened) with their message ID; map it back to `notification_id` and update status. Use a high-write store like Cassandra or DynamoDB.

**11. How do you send scheduled notifications (e.g., "remind me in 2 hours")?**
Store them with a `send_at` timestamp. A scheduler (see [18-Job-Scheduler](../../18-Job-Scheduler/README.md)) polls for due items and publishes them to the channel topics. Respect quiet hours in the user's time zone.

**12. How do you avoid overwhelming one user?**
A per-user rate limit (e.g., max 5 push notifications per hour), collapsing similar events into a digest ("3 new comments"), and quiet hours.

**Likely follow-ups:** multi-language templates · provider failover · in-app notification inbox (store and fetch, plus real-time via WebSocket) · how to test templates safely (preview and allow-list in staging).
