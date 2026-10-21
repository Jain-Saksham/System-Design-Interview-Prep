package com.sdprep.notification;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Runnable walkthrough with simulated time: preferences, a flaky SMS provider, retries, deduplication. */
public class NotificationDemo {

    private static final AtomicLong simulatedMillis = new AtomicLong();

    public static void main(String[] args) {
        UserDirectory users = new UserDirectory();
        users.save(new UserProfile("u42", "asha@example.com", "+91-98000-00042", "device-abc", Set.of(Channel.PUSH)));

        TemplateRenderer renderer = new TemplateRenderer(Map.of(
                "order-shipped", "Hi {{name}}, your order {{orderId}} has shipped!"));
        DeliveryQueue queue = new DeliveryQueue();
        NotificationService service = new NotificationService(users, renderer, queue, simulatedMillis::get);

        Map<Channel, ChannelSender> senders = Map.of(
                Channel.EMAIL, printingSender("EMAIL"),
                Channel.SMS, flakySender("SMS", 2), // provider fails twice, then recovers
                Channel.PUSH, printingSender("PUSH"));
        DeliveryWorker worker = new DeliveryWorker(queue, senders, simulatedMillis::get, 4, 1_000);

        NotificationRequest request = new NotificationRequest("order-981-shipped", "u42", "order-shipped",
                Map.of("name", "Asha", "orderId", "#981"), Set.of(Channel.EMAIL, Channel.SMS, Channel.PUSH));

        SendResult result = service.send(request);
        System.out.println("send()   -> queued " + result.queued() + ", skipped " + result.skipped() + " (opted out)");
        System.out.println("retry    -> duplicate=" + service.send(request).duplicate() + " (same idempotency key, nothing re-queued)\n");

        for (long t : new long[]{0, 1_000, 2_000, 3_000}) {
            simulatedMillis.set(t);
            System.out.printf("t=%4d ms  worker run:%n", t);
            if (worker.deliverDueTasks() == 0) {
                System.out.println("           nothing due (SMS retry is backing off)");
            }
        }
        System.out.printf("%nDelivered: %d, dead letters: %d%n", worker.delivered().size(), worker.deadLetters().size());
    }

    private static ChannelSender printingSender(String channel) {
        return (recipient, message) -> System.out.printf("           %-5s to %-17s OK   \"%s\"%n", channel, recipient, message);
    }

    private static ChannelSender flakySender(String channel, int failuresBeforeSuccess) {
        int[] failuresLeft = {failuresBeforeSuccess};
        return (recipient, message) -> {
            if (failuresLeft[0]-- > 0) {
                System.out.printf("           %-5s to %-17s FAILED (provider timeout), will retry%n", channel, recipient);
                throw new ChannelSender.DeliveryFailedException("provider timeout");
            }
            printingSender(channel).send(recipient, message);
        };
    }
}
