package com.sdprep.chat;

/** Wakes up an offline user's phone (APNs/FCM). The message itself is fetched when the app reconnects. */
@FunctionalInterface
public interface PushNotifier {

    void notifyOffline(String userId, Message message);
}
