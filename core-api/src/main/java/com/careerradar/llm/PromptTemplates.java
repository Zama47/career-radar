package com.careerradar.llm;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads {@code src/main/resources/prompts/*.st.md} and substitutes {@code {{placeholders}}}.
 * Only placeholders found in the template are replaced, so user content that happens to
 * contain braces is passed through untouched.
 */
@Component
public class PromptTemplates {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String render(String name, Map<String, String> variables) {
        String template = cache.computeIfAbsent(name, PromptTemplates::load);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value == null) {
                throw new IllegalStateException(
                        "Prompt " + name + " has unresolved placeholder " + key);
            }
            result.append(template, last, matcher.start());
            result.append(value);
            last = matcher.end();
        }
        result.append(template, last, template.length());
        return result.toString();
    }

    private static String load(String name) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + name);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Prompt " + name + " is missing", ex);
        }
    }
}
