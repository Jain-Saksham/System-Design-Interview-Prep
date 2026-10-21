package com.sdprep.newsfeed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/** All posts, plus each author's posts by recency. Production: Cassandra/MySQL sharded by post or author ID. */
public class PostStore {

    private final Map<Long, Post> postsById = new HashMap<>();
    private final Map<String, NavigableMap<Long, Post>> postsByAuthor = new HashMap<>();

    public synchronized void save(Post post) {
        postsById.put(post.id(), post);
        postsByAuthor.computeIfAbsent(post.authorId(), author -> new TreeMap<>()).put(post.id(), post);
    }

    public synchronized Optional<Post> find(long postId) {
        return Optional.ofNullable(postsById.get(postId));
    }

    /** An author's newest posts with ID below {@code beforeId}, newest first. */
    public synchronized List<Post> recentByAuthor(String authorId, long beforeId, int limit) {
        NavigableMap<Long, Post> posts = postsByAuthor.getOrDefault(authorId, new TreeMap<>());
        return posts.headMap(beforeId, false).descendingMap().values().stream().limit(limit).toList();
    }
}
