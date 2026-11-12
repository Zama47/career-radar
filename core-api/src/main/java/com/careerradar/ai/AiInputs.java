package com.careerradar.ai;

import com.careerradar.common.error.BadRequestException;
import com.careerradar.resume.Resume;
import com.careerradar.vacancy.Vacancy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.StringJoiner;

/**
 * Builds the truncated plain-text inputs sent to the LLM. Truncation is deliberately
 * character-based: it is predictable and keeps prompts inside the configured budget.
 */
final class AiInputs {

    static final int RESUME_MAX_CHARS = 8_000;
    static final int VACANCY_MAX_CHARS = 6_000;
    static final int RESUME_JSON_MAX_CHARS = 12_000;

    private AiInputs() {
    }

    static String resumeText(Resume resume) {
        String text = resume.getContentText();
        if (text == null || text.isBlank()) {
            throw new BadRequestException(
                    "У резюме пустой contentText — заполните текст резюме перед AI-запросом");
        }
        return truncate(text.trim(), RESUME_MAX_CHARS);
    }

    static String vacancyText(Vacancy vacancy) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("Название: " + vacancy.getTitle());
        if (vacancy.getCompany() != null) {
            joiner.add("Компания: " + vacancy.getCompany().getName());
        }
        if (vacancy.getProfession() != null) {
            joiner.add("Профессия: " + vacancy.getProfession().getName()
                    + " (" + vacancy.getProfession().getCode() + ")");
        }
        joiner.add("Грейд: " + vacancy.getSeniority());
        joiner.add("Формат работы: " + vacancy.getRemoteType());
        if (vacancy.getLocation() != null) {
            joiner.add("Локация: " + vacancy.getLocation());
        }
        if (vacancy.getSalaryFrom() != null || vacancy.getSalaryTo() != null) {
            joiner.add("Зарплата: " + salary(vacancy));
        }
        String description = vacancy.getDescription();
        if (description != null && !description.isBlank()) {
            joiner.add("Описание:");
            joiner.add(truncate(description.trim(), VACANCY_MAX_CHARS));
        }
        return joiner.toString();
    }

    static String resumeJson(String json) {
        return truncate(json == null ? "{}" : json, RESUME_JSON_MAX_CHARS);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    static String truncate(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n…[обрезано]";
    }

    private static String salary(Vacancy vacancy) {
        StringBuilder builder = new StringBuilder();
        if (vacancy.getSalaryFrom() != null) {
            builder.append("от ").append(vacancy.getSalaryFrom().stripTrailingZeros().toPlainString());
        }
        if (vacancy.getSalaryTo() != null) {
            if (!builder.isEmpty()) {
                builder.append(" ");
            }
            builder.append("до ").append(vacancy.getSalaryTo().stripTrailingZeros().toPlainString());
        }
        if (vacancy.getCurrency() != null) {
            builder.append(" ").append(vacancy.getCurrency());
        }
        if (vacancy.getSalaryPeriod() != null) {
            builder.append(" / ").append(vacancy.getSalaryPeriod());
        }
        return builder.toString();
    }
}
