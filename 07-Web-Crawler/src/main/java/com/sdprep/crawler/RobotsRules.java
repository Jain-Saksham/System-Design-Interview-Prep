package com.sdprep.crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The "Disallow" rules from a site's robots.txt that apply to all crawlers ({@code User-agent: *}).
 * Simplified: supports path prefixes only (no Allow lines, wildcards, or Crawl-delay).
 */
public class RobotsRules {

    private final List<String> disallowedPrefixes;

    private RobotsRules(List<String> disallowedPrefixes) {
        this.disallowedPrefixes = List.copyOf(disallowedPrefixes);
    }

    public static RobotsRules allowAll() {
        return new RobotsRules(List.of());
    }

    public static RobotsRules parse(String robotsTxt) {
        List<String> disallowed = new ArrayList<>();
        boolean inGroupForAllAgents = false;
        for (String rawLine : robotsTxt.split("\\R")) {
            String line = rawLine.replaceFirst("#.*", "").trim(); // drop comments
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("user-agent:")) {
                inGroupForAllAgents = line.substring("user-agent:".length()).trim().equals("*");
            } else if (inGroupForAllAgents && lower.startsWith("disallow:")) {
                String path = line.substring("disallow:".length()).trim();
                if (!path.isEmpty()) { // an empty Disallow means "allow everything"
                    disallowed.add(path);
                }
            }
        }
        return new RobotsRules(disallowed);
    }

    public boolean isAllowed(String path) {
        return disallowedPrefixes.stream().noneMatch(path::startsWith);
    }
}
