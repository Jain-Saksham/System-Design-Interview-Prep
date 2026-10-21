package com.sdprep.notification;

import com.sdprep.notification.ChannelSender.DeliveryFailedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Takes due tasks from the queue and sends them. Failures are retried with exponential backoff;
 * after {@code maxAttempts} the task goes to a dead-letter list for inspection.
 */
public class DeliveryWorker {

    private final DeliveryQueue queue;
    private final Map<Channel, ChannelSender> sendersByChannel;
    private final LongSupplier clockMillis;
    private final int maxAttempts;
    private final long baseBackoffMillis;

    private final List<DeliveryTask> delivered = new ArrayList<>();
    private final List<DeliveryTask> deadLetters = new ArrayList<>();

    public DeliveryWorker(DeliveryQueue queue, Map<Channel, ChannelSender> sendersByChannel,
                          LongSupplier clockMillis, int maxAttempts, long baseBackoffMillis) {
        this.queue = queue;
        this.sendersByChannel = Map.copyOf(sendersByChannel);
        this.clockMillis = clockMillis;
        this.maxAttempts = maxAttempts;
        this.baseBackoffMillis = baseBackoffMillis;
    }

    /** Processes every task that is due now. Returns how many tasks were attempted. */
    public int deliverDueTasks() {
        long now = clockMillis.getAsLong();
        int attempted = 0;
        for (Optional<DeliveryTask> task = queue.pollDue(now); task.isPresent(); task = queue.pollDue(now)) {
            attemptDelivery(task.get(), now);
            attempted++;
        }
        return attempted;
    }

    private void attemptDelivery(DeliveryTask task, long now) {
        try {
            sendersByChannel.get(task.channel()).send(task.recipient(), task.message());
            delivered.add(task);
        } catch (DeliveryFailedException e) {
            if (task.attempt() >= maxAttempts) {
                deadLetters.add(task); // stop retrying; alert and inspect manually
            } else {
                queue.add(task.nextAttempt(now + backoffAfter(task.attempt())));
            }
        }
    }

    /** 1st failure → base, 2nd → 2×base, 3rd → 4×base ... gives a struggling provider room to recover. */
    long backoffAfter(int attempt) {
        return baseBackoffMillis * (1L << (attempt - 1));
    }

    public List<DeliveryTask> delivered() {
        return List.copyOf(delivered);
    }

    public List<DeliveryTask> deadLetters() {
        return List.copyOf(deadLetters);
    }
}
