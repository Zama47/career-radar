package com.careerradar.profession;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Profession dictionary entry used by the web filter.
 */
@Schema(name = "Profession")
public record ProfessionItem(String code, String name) {
}
