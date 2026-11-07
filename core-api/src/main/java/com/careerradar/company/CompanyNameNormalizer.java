package com.careerradar.company;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Builds the company deduplication key: lower case, unified quotes/punctuation,
 * collapsed whitespace, and legal forms removed ("ООО «Ромашка»" -> "ромашка").
 * Legal-form removal only applies to whole tokens, so names like "Альфа-Банк" survive.
 */
@Component
public class CompanyNameNormalizer {

    private static final Set<String> LEGAL_FORMS = Set.of(
            "ооо", "оао", "зао", "пао", "ао", "ип", "нко",
            "llc", "ltd", "inc", "corp", "gmbh", "co");

    public String normalize(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "";
        }

        String cleaned = rawName.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N}\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.isEmpty()) {
            return "";
        }

        List<String> tokens = new ArrayList<>(List.of(cleaned.split(" ")));
        tokens.removeIf(LEGAL_FORMS::contains);

        String withoutLegalForm = String.join(" ", tokens).trim();
        return withoutLegalForm.isEmpty() ? cleaned : withoutLegalForm;
    }
}
