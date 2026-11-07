package com.careerradar.vacancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ContentHashTest {

    private final ContentHash contentHash = new ContentHash();

    @Test
    void isStableAndProducesSha256Hex() {
        String first = contentHash.compute(
                "Android Developer", "acme", "Москва", new BigDecimal("100000"), new BigDecimal("150000"), "RUB");
        String second = contentHash.compute(
                "Android Developer", "acme", "Москва", new BigDecimal("100000"), new BigDecimal("150000"), "RUB");

        assertThat(first).isEqualTo(second);
        assertThat(first).matches("[0-9a-f]{64}");
    }

    @Test
    void ignoresCaseAndExtraWhitespace() {
        String compact = contentHash.compute(
                "Android Developer", "acme", "Москва", null, null, "RUB");
        String noisy = contentHash.compute(
                "  android   developer ", " ACME ", " москва ", null, null, "rub");

        assertThat(noisy).isEqualTo(compact);
    }

    @Test
    void changesWithSalaryLocationAndCurrency() {
        String base = contentHash.compute(
                "Android Developer", "acme", "Москва", new BigDecimal("100000"), new BigDecimal("150000"), "RUB");

        assertThat(contentHash.compute(
                "Android Developer", "acme", "Москва", new BigDecimal("120000"), new BigDecimal("150000"), "RUB"))
                .isNotEqualTo(base);
        assertThat(contentHash.compute(
                "Android Developer", "acme", "Санкт-Петербург", new BigDecimal("100000"), new BigDecimal("150000"), "RUB"))
                .isNotEqualTo(base);
        assertThat(contentHash.compute(
                "Android Developer", "acme", "Москва", new BigDecimal("100000"), new BigDecimal("150000"), "USD"))
                .isNotEqualTo(base);
    }

    @Test
    void treatsMissingAndEmptyValuesTheSame() {
        assertThat(contentHash.compute("Developer", null, null, null, null, null))
                .isEqualTo(contentHash.compute("Developer", "  ", "", null, null, ""));
    }
}
