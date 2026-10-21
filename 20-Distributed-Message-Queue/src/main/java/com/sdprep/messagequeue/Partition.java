package com.sdprep.messagequeue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * An append-only log. Messages are never modified or removed by consumers; reading doesn't delete.
 * Old messages are removed only by retention, which advances the earliest available offset.
 *
 * <p>Production: segment files on disk, written sequentially (fast even on HDDs) and replicated to followers.
 */
class Partition {

    private final String topic;
    private final int id;
    private final Deque<Record> log = new ArrayDeque<>();
    private long nextOffset = 0;

    Partition(String topic, int id) {
        this.topic = topic;
        this.id = id;
    }

    synchronized Record append(String key, String value) {
        Record record = new Record(topic, id, nextOffset++, key, value);
        log.addLast(record);
        return record;
    }

    /** Up to {@code max} records starting at {@code fromOffset} (or at the earliest retained one, if older). */
    synchronized List<Record> read(long fromOffset, int max) {
        List<Record> batch = new ArrayList<>();
        for (Record record : log) {
            if (record.offset() >= fromOffset && batch.size() < max) {
                batch.add(record);
            }
        }
        return batch;
    }

    /** Keeps only the newest {@code maxRecords}. Production retains by time (e.g. 7 days) or size. */
    synchronized int applyRetention(int maxRecords) {
        int removed = 0;
        while (log.size() > maxRecords) {
            log.removeFirst();
            removed++;
        }
        return removed;
    }

    synchronized long earliestOffset() {
        return log.isEmpty() ? nextOffset : log.peekFirst().offset();
    }

    synchronized long endOffset() {
        return nextOffset;
    }
}
