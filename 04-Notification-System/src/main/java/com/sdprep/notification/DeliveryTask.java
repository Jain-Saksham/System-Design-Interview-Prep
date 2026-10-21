package com.sdprep.notification;

/** One message to one address on one channel. {@code attempt} starts at 1. */
public record DeliveryTask(String notificationId, Channel channel, String recipient, String message,
                           int attempt, long dueAtMillis) {

    public DeliveryTask nextAttempt(long dueAtMillis) {
        return new DeliveryTask(notificationId, channel, recipient, message, attempt + 1, dueAtMillis);
    }
}
