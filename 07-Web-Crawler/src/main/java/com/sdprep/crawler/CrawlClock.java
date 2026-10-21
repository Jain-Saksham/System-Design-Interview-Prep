package com.sdprep.crawler;

/** Time for politeness waits. The simulated version lets tests and the demo "wait" instantly. */
public interface CrawlClock {

    long nowMillis();

    void sleepUntil(long millis);

    static CrawlClock system() {
        return new CrawlClock() {
            public long nowMillis() {
                return System.currentTimeMillis();
            }

            public void sleepUntil(long millis) {
                long waitMillis = millis - nowMillis();
                if (waitMillis > 0) {
                    try {
                        Thread.sleep(waitMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
    }

    /** Starts at 0 and jumps forward when asked to sleep. */
    static CrawlClock simulated() {
        return new CrawlClock() {
            private long now = 0;

            public long nowMillis() {
                return now;
            }

            public void sleepUntil(long millis) {
                now = Math.max(now, millis);
            }
        };
    }
}
