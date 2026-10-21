package com.sdprep.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServiceTest {

    private final DeliveryQueue queue = new DeliveryQueue();
    private NotificationService service;

    @BeforeEach
    void setUp() {
        UserDirectory users = new UserDirectory();
        users.save(new UserProfile("u1", "u1@example.com", "+100", null, Set.of(Channel.SMS)));
        TemplateRenderer renderer = new TemplateRenderer(Map.of("hello", "Hello {{name}}"));
        service = new NotificationService(users, renderer, queue, () -> 0L);
    }

    @Test
    void queuesOneTaskPerAllowedChannel() {
        SendResult result = service.send(request("key-1", Set.of(Channel.EMAIL)));

        assertEquals(Set.of(Channel.EMAIL), result.queued());
        DeliveryTask task = queue.pollDue(0).orElseThrow();
        assertEquals("u1@example.com", task.recipient());
        assertEquals("Hello Asha", task.message());
        assertEquals(1, task.attempt());
    }

    @Test
    void skipsOptedOutChannelsAndChannelsWithoutAnAddress() {
        SendResult result = service.send(request("key-1", Set.of(Channel.EMAIL, Channel.SMS, Channel.PUSH)));

        assertEquals(Set.of(Channel.EMAIL), result.queued());
        assertEquals(Set.of(Channel.SMS, Channel.PUSH), result.skipped()); // SMS opted out, no push token
        assertEquals(1, queue.size());
    }

    @Test
    void sameIdempotencyKeyIsOnlyQueuedOnce() {
        SendResult first = service.send(request("key-1", Set.of(Channel.EMAIL)));
        SendResult retry = service.send(request("key-1", Set.of(Channel.EMAIL)));

        assertFalse(first.duplicate());
        assertTrue(retry.duplicate());
        assertEquals(first.notificationId(), retry.notificationId());
        assertEquals(1, queue.size());
    }

    @Test
    void unknownUserIsRejected() {
        NotificationRequest unknown = new NotificationRequest("k", "ghost", "hello", Map.of("name", "x"), Set.of(Channel.EMAIL));
        assertThrows(IllegalArgumentException.class, () -> service.send(unknown));
    }

    private static NotificationRequest request(String key, Set<Channel> channels) {
        return new NotificationRequest(key, "u1", "hello", Map.of("name", "Asha"), channels);
    }
}
