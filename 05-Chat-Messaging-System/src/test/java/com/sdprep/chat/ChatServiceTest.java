package com.sdprep.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceTest {

    private final List<String> pushedTo = new ArrayList<>();
    private ChatService chat;

    @BeforeEach
    void setUp() {
        chat = new ChatService(new MessageStore(), new ReceiptCursors(),
                (userId, message) -> pushedTo.add(userId), () -> 0L);
        chat.createConversation("dm", Set.of("alice", "bob"));
    }

    @Test
    void onlineMemberReceivesMessageImmediately() {
        List<Message> bobInbox = connect("bob");

        chat.sendMessage("alice", "dm", "m1", "hi");

        assertEquals(List.of("hi"), texts(bobInbox));
        assertTrue(pushedTo.isEmpty());
    }

    @Test
    void offlineMemberGetsPushThenSyncsMissedMessagesInOrderOnReconnect() {
        chat.sendMessage("alice", "dm", "m1", "one");
        chat.sendMessage("alice", "dm", "m2", "two");
        assertEquals(List.of("bob", "bob"), pushedTo);

        List<Message> bobInbox = connect("bob");

        assertEquals(List.of("one", "two"), texts(bobInbox));
    }

    @Test
    void reconnectOnlySyncsMessagesNotYetDelivered() {
        List<Message> firstSession = connect("bob");
        chat.sendMessage("alice", "dm", "m1", "seen already");
        chat.disconnect("bob");
        chat.sendMessage("alice", "dm", "m2", "missed");

        List<Message> secondSession = connect("bob");

        assertEquals(List.of("seen already"), texts(firstSession));
        assertEquals(List.of("missed"), texts(secondSession));
    }

    @Test
    void resendWithSameClientIdIsStoredAndDeliveredOnce() {
        List<Message> bobInbox = connect("bob");

        Message original = chat.sendMessage("alice", "dm", "m1", "hi");
        Message resend = chat.sendMessage("alice", "dm", "m1", "hi");

        assertEquals(original, resend);
        assertEquals(1, bobInbox.size());
    }

    @Test
    void statusProgressesFromSentToDeliveredToRead() {
        Message message = chat.sendMessage("alice", "dm", "m1", "hi");
        assertEquals(MessageStatus.SENT, chat.statusOf("dm", message.sequence()));

        connect("bob");
        assertEquals(MessageStatus.DELIVERED, chat.statusOf("dm", message.sequence()));

        chat.markRead("bob", "dm", message.sequence());
        assertEquals(MessageStatus.READ, chat.statusOf("dm", message.sequence()));
    }

    @Test
    void groupMessageIsDeliveredOnlyWhenEveryRecipientHasIt() {
        chat.createConversation("group", Set.of("alice", "bob", "carol"));
        connect("bob");
        Message message = chat.sendMessage("alice", "group", "g1", "hello all");

        assertEquals(MessageStatus.SENT, chat.statusOf("group", message.sequence())); // carol offline
        connect("carol");
        assertEquals(MessageStatus.DELIVERED, chat.statusOf("group", message.sequence()));
    }

    @Test
    void nonMemberCannotSend() {
        assertThrows(IllegalArgumentException.class, () -> chat.sendMessage("mallory", "dm", "x", "spam"));
    }

    private List<Message> connect(String userId) {
        List<Message> inbox = new ArrayList<>();
        chat.connect(userId, inbox::add);
        return inbox;
    }

    private static List<String> texts(List<Message> messages) {
        return messages.stream().map(Message::text).toList();
    }
}
