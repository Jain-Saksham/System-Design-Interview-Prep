package com.sdprep.notification;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** User contact info and preferences. Production: a user/preferences DB with a read cache. */
public class UserDirectory {

    private final Map<String, UserProfile> profilesById = new ConcurrentHashMap<>();

    public void save(UserProfile profile) {
        profilesById.put(profile.userId(), profile);
    }

    public Optional<UserProfile> find(String userId) {
        return Optional.ofNullable(profilesById.get(userId));
    }
}
