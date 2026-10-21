package com.sdprep.chat;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Runnable walkthrough: live delivery, offline push + sync, deduplicated resend, read receipts. */
public class ChatDemo {

    public static void main(String[] args) {
        AtomicLong clock = new AtomicLong(1_000);
        PushNotifier push = (userId, message) ->
                System.out.printf("  [push → %s] new message from %s%n", userId, message.senderId());
        ChatService chat = new ChatService(new MessageStore(), new ReceiptCursors(), push, clock::get);

        chat.createConversation("trip", Set.of("alice", "bob", "carol"));
        chat.connect("alice", device("alice"));
        chat.connect("carol", device("carol"));
        System.out.println("alice and carol are online; bob is offline\n");

        System.out.println("alice sends two messages:");
        Message first = chat.sendMessage("alice", "trip", "a-1", "Flight lands at 6pm");
        chat.sendMessage("alice", "trip", "a-2", "Who's picking us up?");
        System.out.println("  status of #1: " + chat.statusOf("trip", first.sequence()) + " (bob hasn't received it)\n");

        System.out.println("alice's app retries message a-2 after a network timeout:");
        Message resent = chat.sendMessage("alice", "trip", "a-2", "Who's picking us up?");
        System.out.println("  server returns existing #" + resent.sequence() + ", nothing delivered twice\n");

        System.out.println("bob comes online and syncs what he missed:");
        chat.connect("bob", device("bob"));
        System.out.println("  status of #1: " + chat.statusOf("trip", first.sequence()) + "\n");

        System.out.println("bob and carol read up to #2:");
        chat.markRead("bob", "trip", 2);
        chat.markRead("carol", "trip", 2);
        System.out.println("  status of #1: " + chat.statusOf("trip", first.sequence()));
    }

    private static ClientConnection device(String userId) {
        return message -> System.out.printf("  [%s's device] #%d %s: %s%n",
                userId, message.sequence(), message.senderId(), message.text());
    }
}
