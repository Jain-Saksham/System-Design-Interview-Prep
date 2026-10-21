package com.sdprep.messagequeue;

/**
 * A stored message.
 *
 * @param offset its position in the partition: assigned on append, never reused, increasing
 */
public record Record(String topic, int partition, long offset, String key, String value) {
}
