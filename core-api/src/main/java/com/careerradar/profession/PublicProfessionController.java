package com.careerradar.profession;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only profession dictionary for filter dropdowns.
 */
@RestController
@RequestMapping("/api/v1/professions")
@Tag(name = "professions", description = "Profession dictionary")
@SecurityRequirement(name = "bearerAuth")
public class PublicProfessionController {

    private final ProfessionRepository professionRepository;

    public PublicProfessionController(ProfessionRepository professionRepository) {
        this.professionRepository = professionRepository;
    }

    @GetMapping
    @Operation(summary = "List all known professions")
    public List<ProfessionItem> list() {
        return professionRepository.findAllByOrderByNameAsc().stream()
                .map(profession -> new ProfessionItem(profession.getCode(), profession.getName()))
                .toList();
    }
}
