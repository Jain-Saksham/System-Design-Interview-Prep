package com.sdprep.notification;

import java.util.Map;
import java.util.Set;

/**
 * A request from another service, e.g. "send the order-shipped message to user 42 by email and SMS".
 *
 * @param idempotencyKey chosen by the caller (e.g. "order-981-shipped"); a retried request with the
 *                       same key is not sent twice
 */
public record NotificationRequest(String idempotencyKey, String userId, String templateId,
                                  Map<String, String> params, Set<Channel> channels) {
}
