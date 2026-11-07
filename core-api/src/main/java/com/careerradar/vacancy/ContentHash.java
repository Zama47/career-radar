package com.careerradar.vacancy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Content fingerprint used for cross-source deduplication.
 * SHA-256 over normalized title, company, location and salary. Text is lower-cased and
 * whitespace-collapsed so cosmetic source differences do not create duplicates.
 */
@Component
public class ContentHash {

    private static final String SEPARATOR = "\u001f";

    public String compute(
            String title,
            String company,
            String location,
            BigDecimal salaryFrom,
            BigDecimal salaryTo,
            String currency) {
        String input = String.join(SEPARATOR,
                normalizeText(title),
                normalizeText(company),
                normalizeText(location),
                normalizeAmount(salaryFrom),
                normalizeAmount(salaryTo),
                normalizeText(currency));
        return sha256Hex(input);
    }

    static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    static String normalizeAmount(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }
}
