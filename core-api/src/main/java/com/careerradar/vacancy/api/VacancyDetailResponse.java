package com.careerradar.vacancy.api;

import com.careerradar.skill.SkillSource;
import com.careerradar.vacancy.RemoteType;
import com.careerradar.vacancy.SalaryPeriod;
import com.careerradar.vacancy.Seniority;
import com.careerradar.vacancy.VacancyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full vacancy card, including description, detected skills and the original source.
 */
@Schema(name = "VacancyDetail")
public record VacancyDetailResponse(
        UUID id,
        String title,
        String description,
        String company,
        String companyWebsite,
        @Schema(description = "Profession code", example = "ANDROID") String profession,
        String professionName,
        Seniority seniority,
        RemoteType remoteType,
        String location,
        BigDecimal salaryFrom,
        BigDecimal salaryTo,
        String currency,
        Boolean salaryGross,
        SalaryPeriod salaryPeriod,
        Instant publishedAt,
        Instant firstSeenAt,
        Instant lastSeenAt,
        VacancyStatus status,
        @Schema(description = "Original vacancy URL") String sourceUrl,
        @Schema(description = "Source code", example = "MTS") String source,
        String sourceName,
        List<SkillItem> skills) {

    @Schema(name = "VacancySkill")
    public record SkillItem(String code, String name, BigDecimal confidence, SkillSource source) {
    }
}
