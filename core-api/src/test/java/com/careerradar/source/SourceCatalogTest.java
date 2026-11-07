package com.careerradar.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.careerradar.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * V4__source_catalog.sql must make every agreed source code available to the internal upsert
 * endpoint, otherwise the collector's first batch is rejected with "Unknown vacancy source".
 */
class SourceCatalogTest extends AbstractIntegrationTest {

    private static final List<String> REQUIRED_CODES = List.of(
            "MTS", "YANDEX", "VK", "SBER", "TBANK", "OZON", "AVITO", "X5", "MAGNIT", "LAMODA",
            "RWB", "ROSTELECOM", "BEELINE", "MEGAFON", "T2", "KASPERSKY", "POSITIVE", "YADRO",
            "SELECTEL", "CLOUD_RU", "T1", "IBS", "JET", "CROC", "SMART_ENGINES", "KODE",
            "NEOFLEX", "SIMBIRSOFT", "ASTON", "AXENIX", "NAUMEN", "POSTGRES_PRO", "YUMONEY",
            "KONTUR", "SIBUR", "GAZPROMBANK", "ALFA", "VTB", "RTLABS", "DIGITAL_DESIGN", "1C");

    @Test
    void seedsEveryAgreedSourceWithBaseUrl() {
        for (String code : REQUIRED_CODES) {
            var row = jdbcTemplate.queryForMap(
                    "select code, name, base_url from sources where code = ?", code);
            assertThat(row.get("name")).as("name of %s", code).isNotNull();
            assertThat(row.get("base_url")).as("base_url of %s", code).isNotNull();
        }
    }

    @Test
    void keepsLegacySourcesFromEarlierMigrations() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sources where code in ('HH', 'OTHER')", Integer.class))
                .isEqualTo(2);
    }
}
