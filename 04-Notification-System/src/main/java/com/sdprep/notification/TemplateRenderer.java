package com.sdprep.notification;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fills {{placeholders}} in stored templates, e.g. "Hi {{name}}" + {name=Asha} → "Hi Asha". */
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final Map<String, String> templatesById;

    public TemplateRenderer(Map<String, String> templatesById) {
        this.templatesById = Map.copyOf(templatesById);
    }

    public String render(String templateId, Map<String, String> params) {
        String template = templatesById.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Unknown template: " + templateId);
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder message = new StringBuilder();
        while (matcher.find()) {
            String value = params.get(matcher.group(1));
            if (value == null) {
                // Fail loudly: sending "Hi {{name}}" to a customer is worse than not sending.
                throw new IllegalArgumentException("Missing parameter '" + matcher.group(1) + "' for " + templateId);
            }
            matcher.appendReplacement(message, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(message);
        return message.toString();
    }
}
