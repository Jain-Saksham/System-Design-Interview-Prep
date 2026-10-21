package com.sdprep.chat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per member, per conversation: the highest sequence delivered to their device and the highest read.
 * Storing one number per member (not one flag per message) keeps receipts cheap: "read up to 57"
 * covers messages 1..57.
 */
public class ReceiptCursors {

    private record Key(String conversationId, String userId) {
    }

    private final Map<Key, Long> deliveredUpTo = new ConcurrentHashMap<>();
    private final Map<Key, Long> readUpTo = new ConcurrentHashMap<>();

    public void markDelivered(String conversationId, String userId, long sequence) {
        deliveredUpTo.merge(new Key(conversationId, userId), sequence, Math::max); // cursors only move forward
    }

    /** Reading implies delivery. */
    public void markRead(String conversationId, String userId, long sequence) {
        markDelivered(conversationId, userId, sequence);
        readUpTo.merge(new Key(conversationId, userId), sequence, Math::max);
    }

    public long deliveredUpTo(String conversationId, String userId) {
        return deliveredUpTo.getOrDefault(new Key(conversationId, userId), 0L);
    }

    public long readUpTo(String conversationId, String userId) {
        return readUpTo.getOrDefault(new Key(conversationId, userId), 0L);
    }
}
