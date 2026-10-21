package com.sdprep.notification;

import java.util.Set;

/**
 * @param duplicate true if this idempotency key was already processed (nothing new was queued)
 * @param queued    channels that got a delivery task
 * @param skipped   channels skipped because the user opted out or has no address for them
 */
public record SendResult(String notificationId, boolean duplicate, Set<Channel> queued, Set<Channel> skipped) {

    SendResult asDuplicate() {
        return new SendResult(notificationId, true, queued, skipped);
    }
}
