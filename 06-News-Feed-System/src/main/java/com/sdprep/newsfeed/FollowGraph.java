package com.sdprep.newsfeed;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Who follows whom, stored in both directions so both lookups are fast. Production: a graph DB or two tables. */
public class FollowGraph {

    private final Map<String, Set<String>> followersByUser = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> followingByUser = new ConcurrentHashMap<>();

    public void follow(String followerId, String followeeId) {
        followersByUser.computeIfAbsent(followeeId, id -> ConcurrentHashMap.newKeySet()).add(followerId);
        followingByUser.computeIfAbsent(followerId, id -> ConcurrentHashMap.newKeySet()).add(followeeId);
    }

    public void unfollow(String followerId, String followeeId) {
        followersByUser.getOrDefault(followeeId, Set.of()).remove(followerId);
        followingByUser.getOrDefault(followerId, Set.of()).remove(followeeId);
    }

    public Set<String> followersOf(String userId) {
        return Set.copyOf(followersByUser.getOrDefault(userId, Set.of()));
    }

    public Set<String> followedBy(String userId) {
        return Set.copyOf(followingByUser.getOrDefault(userId, Set.of()));
    }

    public int followerCount(String userId) {
        return followersByUser.getOrDefault(userId, Set.of()).size();
    }
}
