package com.careerradar.company;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompanyNameNormalizerTest {

    private final CompanyNameNormalizer normalizer = new CompanyNameNormalizer();

    @Test
    void removesLegalFormAndQuotes() {
        assertThat(normalizer.normalize("ООО «Ромашка»")).isEqualTo("ромашка");
        assertThat(normalizer.normalize("АО \"Тинькофф Банк\"")).isEqualTo("тинькофф банк");
        assertThat(normalizer.normalize("ПАО Сбербанк")).isEqualTo("сбербанк");
    }

    @Test
    void removesLatinLegalSuffixes() {
        assertThat(normalizer.normalize("Acme LLC")).isEqualTo("acme");
        assertThat(normalizer.normalize("  Acme,   Inc. ")).isEqualTo("acme");
    }

    @Test
    void collapsesWhitespaceAndCase() {
        assertThat(normalizer.normalize("  Yandex   Technology ")).isEqualTo("yandex technology");
        assertThat(normalizer.normalize("Ёлки-Палки")).isEqualTo("елки-палки");
    }

    @Test
    void keepsNamesThatAreOnlyALegalForm() {
        assertThat(normalizer.normalize("ООО")).isEqualTo("ооо");
    }

    @Test
    void handlesBlankInput() {
        assertThat(normalizer.normalize(null)).isEmpty();
        assertThat(normalizer.normalize("   ")).isEmpty();
        assertThat(normalizer.normalize("«»")).isEmpty();
    }
}
