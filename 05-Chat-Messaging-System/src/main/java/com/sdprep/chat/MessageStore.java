package com.sdprep.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Append-only message log per conversation.
 *
 * <p>Production: Cassandra/ScyllaDB with partition key {@code conversation_id} and clustering key
 * {@code sequence}. Reads like "messages after N" are a single-partition range scan.
 */
public class MessageStore {

    /** @param isNew false if this clientMessageId was already stored (a resend) */
    public record AppendResult(Message message, boolean isNew) {
    }

    private final Map<String, ConversationLog> logsByConversation = new ConcurrentHashMap<>();

    public AppendResult append(String conversationId, String senderId, String clientMessageId,
                               String text, long nowMillis) {
        return logFor(conversationId).append(conversationId, senderId, clientMessageId, text, nowMillis);
    }

    /** Messages with sequence greater than {@code afterSequence}, oldest first. */
    public List<Message> messagesAfter(String conversationId, long afterSequence) {
        return logFor(conversationId).messagesAfter(afterSequence);
    }

    public Optional<Message> find(String conversationId, long sequence) {
        return logFor(conversationId).find(sequence);
    }

    private ConversationLog logFor(String conversationId) {
        return logsByConversation.computeIfAbsent(conversationId, id -> new ConversationLog());
    }

    /** One conversation. Locking per conversation keeps sequences gap-free without a global lock. */
    private static final class ConversationLog {
        private final List<Message> messages = new ArrayList<>();
        private final Map<String, Message> messagesByClientId = new HashMap<>();

        synchronized AppendResult append(String conversationId, String senderId, String clientMessageId,
                                         String text, long nowMillis) {
            Message existing = messagesByClientId.get(clientMessageId);
            if (existing != null) {
                return new AppendResult(existing, false);
            }
            long sequence = messages.size() + 1L;
            Message message = new Message(conversationId, sequence, clientMessageId, senderId, text, nowMillis);
            messages.add(message);
            messagesByClientId.put(clientMessageId, message);
            return new AppendResult(message, true);
        }

        synchronized Optional<Message> find(long sequence) {
            boolean exists = sequence >= 1 && sequence <= messages.size();
            return exists ? Optional.of(messages.get((int) sequence - 1)) : Optional.empty();
        }

        synchronized List<Message> messagesAfter(long afterSequence) {
            int fromIndex = (int) Math.min(Math.max(afterSequence, 0), messages.size()); // sequence N is at index N-1
            return List.copyOf(messages.subList(fromIndex, messages.size()));
        }
    }
}
