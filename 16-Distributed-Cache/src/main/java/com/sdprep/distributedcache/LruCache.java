package com.sdprep.distributedcache;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Fixed-capacity cache that evicts the least recently used entry, with optional per-entry TTL.
 *
 * <p>The classic O(1) design: a hash map finds the entry, and a doubly linked list keeps entries in
 * recency order (most recent right after {@code head}, least recent right before {@code tail}).
 * Every get/put moves the entry to the front; eviction removes from the back.
 */
public class LruCache<K, V> {

    private static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private final class Node {
        K key;
        V value;
        long expiresAtMillis;
        Node previous;
        Node next;
    }

    private final int capacity;
    private final LongSupplier clockMillis;
    private final Map<K, Node> nodesByKey = new HashMap<>();
    private final Node head = new Node(); // sentinel: no data, simplifies edge cases
    private final Node tail = new Node(); // sentinel
    private long evictions;

    public LruCache(int capacity, LongSupplier clockMillis) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.clockMillis = clockMillis;
        head.next = tail;
        tail.previous = head;
    }

    public synchronized Optional<V> get(K key) {
        Node node = nodesByKey.get(key);
        if (node == null) {
            return Optional.empty();
        }
        if (clockMillis.getAsLong() >= node.expiresAtMillis) {
            removeNode(node); // lazy expiry: expired entries are removed when touched
            return Optional.empty();
        }
        moveToFront(node);
        return Optional.of(node.value);
    }

    public synchronized void put(K key, V value) {
        put(key, value, NEVER_EXPIRES);
    }

    /** @param ttlMillis time to live; the entry disappears after this long */
    public synchronized void put(K key, V value, long ttlMillis) {
        long now = clockMillis.getAsLong();
        long expiresAt = ttlMillis > NEVER_EXPIRES - now ? NEVER_EXPIRES : now + ttlMillis; // avoid overflow
        Node node = nodesByKey.get(key);
        if (node != null) {
            node.value = value;
            node.expiresAtMillis = expiresAt;
            moveToFront(node);
            return;
        }
        node = new Node();
        node.key = key;
        node.value = value;
        node.expiresAtMillis = expiresAt;
        nodesByKey.put(key, node);
        addAfterHead(node);
        if (nodesByKey.size() > capacity) {
            removeNode(tail.previous); // least recently used
            evictions++;
        }
    }

    public synchronized boolean remove(K key) {
        Node node = nodesByKey.get(key);
        if (node == null) {
            return false;
        }
        removeNode(node);
        return true;
    }

    public synchronized int size() {
        return nodesByKey.size();
    }

    public synchronized long evictions() {
        return evictions;
    }

    private void moveToFront(Node node) {
        unlink(node);
        addAfterHead(node);
    }

    private void removeNode(Node node) {
        unlink(node);
        nodesByKey.remove(node.key);
    }

    private void addAfterHead(Node node) {
        node.previous = head;
        node.next = head.next;
        head.next.previous = node;
        head.next = node;
    }

    private void unlink(Node node) {
        node.previous.next = node.next;
        node.next.previous = node.previous;
    }
}
