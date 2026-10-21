package com.sdprep.notification;

/** Talks to one external provider: an email service (SES), SMS gateway (Twilio), or push service (APNs/FCM). */
public interface ChannelSender {

    void send(String recipient, String message) throws DeliveryFailedException;

    class DeliveryFailedException extends Exception {
        public DeliveryFailedException(String reason) {
            super(reason);
        }
    }
}
