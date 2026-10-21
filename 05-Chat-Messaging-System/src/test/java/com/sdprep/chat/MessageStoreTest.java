package com.sdprep.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MessageStoreTest {

    private final MessageStore store = new MessageStore();

    @Test
    void sequencesAreIndependentPerConversation() {
        store.append("a", "u1", "c1", "x", 0);
        store.append("a", "u1", "c2", "y", 0);
        Message firstInB = store.append("b", "u1", "c3", "z", 0).message();

        assertEquals(1, firstInB.sequence());
        assertEquals(2, store.messagesAfter("a", 0).size());
    }

    @Test
    void messagesAfterReturnsOnlyNewerMessages() {
        store.append("a", "u1", "c1", "one", 0);
        store.append("a", "u1", "c2", "two", 0);
        store.append("a", "u1", "c3", "three", 0);

        assertEquals(List.of("two", "three"), store.messagesAfter("a", 1).stream().map(Message::text).toList());
        assertEquals(List.of(), store.messagesAfter("a", 3));
    }

    @Test
    void duplicateClientIdReturnsExistingMessage() {
        Message first = store.append("a", "u1", "c1", "hi", 0).message();
        MessageStore.AppendResult again = store.append("a", "u1", "c1", "hi", 5);

        assertFalse(again.isNew());
        assertEquals(first, again.message());
    }

    @Test
    void concurrentAppendsGetUniqueGapFreeSequences() throws InterruptedException {
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            IntStream.range(0, 1_000).forEach(i -> pool.submit(() -> store.append("a", "u" + i, "c" + i, "m", 0)));
        }

        List<Long> sequences = store.messagesAfter("a", 0).stream().map(Message::sequence).toList();
        assertEquals(LongStream.rangeClosed(1, 1_000).boxed().toList(), sequences);
    }
}
