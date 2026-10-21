package com.sdprep.notification;

import java.util.Optional;
import java.util.Set;

/** Contact details and preferences. Any address may be null if the user never provided it. */
public record UserProfile(String userId, String email, String phone, String deviceToken,
                          Set<Channel> optedOutChannels) {

    public Optional<String> addressFor(Channel channel) {
        String address = switch (channel) {
            case EMAIL -> email;
            case SMS -> phone;
            case PUSH -> deviceToken;
        };
        return Optional.ofNullable(address);
    }

    public boolean hasOptedOutOf(Channel channel) {
        return optedOutChannels.contains(channel);
    }
}
