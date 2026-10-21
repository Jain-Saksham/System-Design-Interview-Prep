package com.sdprep.newsfeed;

import java.util.List;

/** @param nextCursor pass to the next getFeed call; null when there are no more posts */
public record FeedPage(List<Post> posts, Long nextCursor) {
}
