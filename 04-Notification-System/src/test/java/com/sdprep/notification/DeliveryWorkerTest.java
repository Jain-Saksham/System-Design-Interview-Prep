package com.sdprep.notification;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryWorkerTest {

    private static final long BASE_BACKOFF = 1_000;
    private static final int MAX_ATTEMPTS = 3;

    private final AtomicLong clock = new AtomicLong();
    private final DeliveryQueue queue = new DeliveryQueue();
    private final List<String> sentEmails = new ArrayList<>();

    @Test
    void deliversDueTasks() {
        DeliveryWorker worker = worker(failingTimes(0));
        queue.add(task(Channel.EMAIL));

        worker.deliverDueTasks();

        assertEquals(List.of("a@example.com"), sentEmails);
        assertEquals(1, worker.delivered().size());
    }

    @Test
    void retriesWithExponentialBackoff() {
        DeliveryWorker worker = worker(failingTimes(2));
        queue.add(task(Channel.SMS));

        worker.deliverDueTasks();                  // attempt 1 fails at t=0 → retry due at 1000
        clock.set(999);
        assertEquals(0, worker.deliverDueTasks()); // not due yet
        clock.set(1_000);
        worker.deliverDueTasks();                  // attempt 2 fails → retry due at 1000 + 2000
        clock.set(2_999);
        assertEquals(0, worker.deliverDueTasks());
        clock.set(3_000);
        worker.deliverDueTasks();                  // attempt 3 succeeds

        assertEquals(1, worker.delivered().size());
        assertEquals(3, worker.delivered().get(0).attempt());
    }

    @Test
    void movesToDeadLettersAfterMaxAttempts() {
        DeliveryWorker worker = worker(alwaysFailing());
        queue.add(task(Channel.SMS));

        for (long t = 0; t <= 10_000; t += 500) {
            clock.set(t);
            worker.deliverDueTasks();
        }

        assertEquals(1, worker.deadLetters().size());
        assertEquals(MAX_ATTEMPTS, worker.deadLetters().get(0).attempt());
        assertTrue(worker.delivered().isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void failingChannelDoesNotBlockOtherChannels() {
        DeliveryWorker worker = worker(alwaysFailing());
        queue.add(task(Channel.SMS));
        queue.add(task(Channel.EMAIL));

        worker.deliverDueTasks();

        assertEquals(List.of("a@example.com"), sentEmails);
    }

    private DeliveryWorker worker(ChannelSender smsSender) {
        ChannelSender emailSender = (recipient, message) -> sentEmails.add(recipient);
        return new DeliveryWorker(queue, Map.of(Channel.EMAIL, emailSender, Channel.SMS, smsSender),
                clock::get, MAX_ATTEMPTS, BASE_BACKOFF);
    }

    private DeliveryTask task(Channel channel) {
        String recipient = channel == Channel.EMAIL ? "a@example.com" : "+100";
        return new DeliveryTask("n1", channel, recipient, "hi", 1, clock.get());
    }

    private static ChannelSender alwaysFailing() {
        return (recipient, message) -> {
            throw new ChannelSender.DeliveryFailedException("down");
        };
    }

    private static ChannelSender failingTimes(int failures) {
        int[] remaining = {failures};
        return (recipient, message) -> {
            if (remaining[0]-- > 0) {
                throw new ChannelSender.DeliveryFailedException("timeout");
            }
        };
    }
}
