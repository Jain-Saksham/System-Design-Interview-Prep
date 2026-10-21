package com.sdprep.newsfeed;

/** Runnable walkthrough: push for regular users, pull for a celebrity, merged and paginated feed. */
public class NewsFeedDemo {

    public static void main(String[] args) {
        FeedCache cache = new FeedCache(500);
        FeedService feeds = new FeedService(new PostStore(), new FollowGraph(), cache, 3, () -> 0L);
        System.out.println("Celebrity threshold: 3 followers (demo scale; real systems use ~10k+)\n");

        feeds.follow("ana", "bob");
        feeds.follow("ana", "taylor");
        feeds.follow("ben", "taylor");
        feeds.follow("cara", "taylor");
        System.out.println("bob has 1 follower    -> celebrity? " + feeds.isCelebrity("bob"));
        System.out.println("taylor has 3 followers -> celebrity? " + feeds.isCelebrity("taylor") + "\n");

        publish(feeds, cache, "bob", "Morning run done");
        publish(feeds, cache, "taylor", "New album out tonight!");
        publish(feeds, cache, "bob", "Coffee anyone?");

        System.out.println("\nana's feed (2 per page):");
        FeedPage page = feeds.getFeed("ana", 2, null);
        int pageNumber = 1;
        while (true) {
            System.out.println("  page " + pageNumber++ + ":");
            page.posts().forEach(post -> System.out.printf("    #%d %-7s %s%n", post.id(), post.authorId(), post.text()));
            if (page.nextCursor() == null) {
                break;
            }
            page = feeds.getFeed("ana", 2, page.nextCursor());
        }
    }

    private static void publish(FeedService feeds, FeedCache cache, String author, String text) {
        long writesBefore = cache.totalWrites();
        Post post = feeds.publish(author, text);
        System.out.printf("%-6s posts #%d \"%s\" -> %d feed write(s)%s%n", author, post.id(), text,
                cache.totalWrites() - writesBefore, feeds.isCelebrity(author) ? " (own feed only; followers pull)" : "");
    }
}
