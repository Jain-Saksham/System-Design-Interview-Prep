package com.sdprep.crawler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobotsRulesTest {

    @Test
    void disallowedPrefixesAreBlocked() {
        RobotsRules rules = RobotsRules.parse("""
                User-agent: *
                Disallow: /admin
                Disallow: /tmp/  # scratch space
                """);

        assertFalse(rules.isAllowed("/admin"));
        assertFalse(rules.isAllowed("/admin/users"));
        assertFalse(rules.isAllowed("/tmp/file"));
        assertTrue(rules.isAllowed("/products"));
    }

    @Test
    void rulesForOtherAgentsAreIgnored() {
        RobotsRules rules = RobotsRules.parse("""
                User-agent: BadBot
                Disallow: /

                User-agent: *
                Disallow: /private
                """);

        assertTrue(rules.isAllowed("/public"));
        assertFalse(rules.isAllowed("/private"));
    }

    @Test
    void emptyDisallowAllowsEverything() {
        assertTrue(RobotsRules.parse("User-agent: *\nDisallow:").isAllowed("/anything"));
    }
}
