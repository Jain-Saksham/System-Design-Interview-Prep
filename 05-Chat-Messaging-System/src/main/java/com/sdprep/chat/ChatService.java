package com.sdprep.chat;

import com.sdprep.chat.MessageStore.AppendResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Chat server logic: store messages, push them to online members, notify offline members,
 * and sync missed messages when a device reconnects.
 */
public class ChatService {

    private final MessageStore store;
    private final ReceiptCursors cursors;
    private final PushNotifier pushNotifier;
    private final LongSupplier clockMillis;

    private final Map<String, Set<String>> membersByConversation = new ConcurrentHashMap<>();
    // Production: a presence/session registry in Redis mapping user → gateway server holding their socket.
    private final Map<String, ClientConnection> onlineConnections = new ConcurrentHashMap<>();

    public ChatService(MessageStore store, ReceiptCursors cursors, PushNotifier pushNotifier, LongSupplier clockMillis) {
        this.store = store;
        this.cursors = cursors;
        this.pushNotifier = pushNotifier;
        this.clockMillis = clockMillis;
    }

    public void createConversation(String conversationId, Set<String> memberIds) {
        // Sorted so fan-out order is the same on every run (easier to read and test).
        membersByConversation.put(conversationId, Collections.unmodifiableSortedSet(new TreeSet<>(memberIds)));
    }

    /** A device comes online: register it and deliver everything it missed, in order. */
    public void connect(String userId, ClientConnection connection) {
        onlineConnections.put(userId, connection);
        membersByConversation.forEach((conversationId, members) -> {
            if (members.contains(userId)) {
                List<Message> missed = store.messagesAfter(conversationId, cursors.deliveredUpTo(conversationId, userId));
                missed.forEach(message -> deliverTo(userId, connection, message));
            }
        });
    }

    public void disconnect(String userId) {
        onlineConnections.remove(userId);
    }

    public boolean isOnline(String userId) {
        return onlineConnections.containsKey(userId);
    }

    /**
     * Stores the message and fans it out. Safe to call again with the same clientMessageId
     * (e.g. the sender's app retried after a timeout): the message is stored and delivered once.
     */
    public Message sendMessage(String senderId, String conversationId, String clientMessageId, String text) {
        Set<String> members = membersOf(conversationId);
        if (!members.contains(senderId)) {
            throw new IllegalArgumentException(senderId + " is not a member of " + conversationId);
        }
        AppendResult result = store.append(conversationId, senderId, clientMessageId, text, clockMillis.getAsLong());
        Message message = result.message();
        if (!result.isNew()) {
            return message; // already stored and fanned out the first time
        }
        cursors.markRead(conversationId, senderId, message.sequence()); // the sender has obviously seen it

        for (String memberId : members) {
            if (memberId.equals(senderId)) {
                continue;
            }
            ClientConnection connection = onlineConnections.get(memberId);
            if (connection != null) {
                deliverTo(memberId, connection, message);
            } else {
                pushNotifier.notifyOffline(memberId, message);
            }
        }
        return message;
    }

    public void markRead(String userId, String conversationId, long upToSequence) {
        cursors.markRead(conversationId, userId, upToSequence);
    }

    /** The sender's tick status, based on every other member's cursors. */
    public MessageStatus statusOf(String conversationId, long sequence) {
        Message message = store.find(conversationId, sequence)
                .orElseThrow(() -> new IllegalArgumentException("No message #" + sequence + " in " + conversationId));
        List<String> recipients = membersOf(conversationId).stream()
                .filter(member -> !member.equals(message.senderId()))
                .toList();
        if (recipients.stream().allMatch(member -> cursors.readUpTo(conversationId, member) >= sequence)) {
            return MessageStatus.READ;
        }
        if (recipients.stream().allMatch(member -> cursors.deliveredUpTo(conversationId, member) >= sequence)) {
            return MessageStatus.DELIVERED;
        }
        return MessageStatus.SENT;
    }

    private void deliverTo(String userId, ClientConnection connection, Message message) {
        connection.deliver(message);
        // Production: the device sends an ack and only then is the cursor advanced.
        cursors.markDelivered(message.conversationId(), userId, message.sequence());
    }

    private Set<String> membersOf(String conversationId) {
        Set<String> members = membersByConversation.get(conversationId);
        if (members == null) {
            throw new IllegalArgumentException("Unknown conversation: " + conversationId);
        }
        return members;
    }
}
