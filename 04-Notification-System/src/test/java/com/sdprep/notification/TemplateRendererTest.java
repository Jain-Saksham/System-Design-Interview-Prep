package com.sdprep.notification;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer(Map.of(
            "welcome", "Hi {{name}}, welcome to {{app}}! Cost: $5"));

    @Test
    void replacesEveryPlaceholder() {
        assertEquals("Hi Asha, welcome to Shopper! Cost: $5",
                renderer.render("welcome", Map.of("name", "Asha", "app", "Shopper")));
    }

    @Test
    void valuesWithSpecialCharactersAreInsertedLiterally() {
        assertEquals("Hi $1 \\o/, welcome to X! Cost: $5",
                renderer.render("welcome", Map.of("name", "$1 \\o/", "app", "X")));
    }

    @Test
    void missingParameterOrTemplateIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> renderer.render("welcome", Map.of("name", "Asha")));
        assertThrows(IllegalArgumentException.class, () -> renderer.render("nope", Map.of()));
    }
}
