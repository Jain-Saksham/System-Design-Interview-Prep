package com.sdprep.newsfeed;

/**
 * @param id increases over time (like a Snowflake ID), so sorting by ID is sorting by recency,
 *           and an ID works as a pagination cursor
 */
public record Post(long id, String authorId, String text, long createdAtMillis) {
}
