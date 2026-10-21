package com.sdprep.notification;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * The API other services call. It validates, applies preferences, renders the message, and queues
 * one task per channel. It returns immediately; delivery happens asynchronously in workers.
 */
public class NotificationService {

    private final UserDirectory users;
    private final TemplateRenderer renderer;
    private final DeliveryQueue queue;
    private final LongSupplier clockMillis;

    // Production: Redis or a DB table with a unique key and a TTL (e.g. 24 hours).
    private final Map<String, SendResult> resultsByIdempotencyKey = new ConcurrentHashMap<>();

    public NotificationService(UserDirectory users, TemplateRenderer renderer,
                               DeliveryQueue queue, LongSupplier clockMillis) {
        this.users = users;
        this.renderer = renderer;
        this.queue = queue;
        this.clockMillis = clockMillis;
    }

    public SendResult send(NotificationRequest request) {
        AtomicBoolean processedNow = new AtomicBoolean(false);
        // computeIfAbsent runs the queuing step at most once per key, even for concurrent retries.
        SendResult result = resultsByIdempotencyKey.computeIfAbsent(request.idempotencyKey(), key -> {
            processedNow.set(true);
            return queueDeliveries(request);
        });
        return processedNow.get() ? result : result.asDuplicate();
    }

    private SendResult queueDeliveries(NotificationRequest request) {
        if (request.channels().isEmpty()) {
            throw new IllegalArgumentException("At least one channel is required");
        }
        UserProfile user = users.find(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + request.userId()));
        String message = renderer.render(request.templateId(), request.params());
        String notificationId = UUID.randomUUID().toString();

        Set<Channel> queued = EnumSet.noneOf(Channel.class);
        Set<Channel> skipped = EnumSet.noneOf(Channel.class);
        for (Channel channel : EnumSet.copyOf(request.channels())) { // EnumSet: stable channel order
            var address = user.addressFor(channel);
            if (user.hasOptedOutOf(channel) || address.isEmpty()) {
                skipped.add(channel);
                continue;
            }
            queue.add(new DeliveryTask(notificationId, channel, address.get(), message, 1, clockMillis.getAsLong()));
            queued.add(channel);
        }
        return new SendResult(notificationId, false, queued, skipped);
    }
}
