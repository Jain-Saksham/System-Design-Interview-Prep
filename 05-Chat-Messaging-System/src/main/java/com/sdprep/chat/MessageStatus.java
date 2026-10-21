package com.sdprep.chat;

/** What the sender sees: one tick, two ticks, blue ticks. */
public enum MessageStatus {
    SENT,       // stored on the server
    DELIVERED,  // every other member's device has received it
    READ        // every other member has read it
}
