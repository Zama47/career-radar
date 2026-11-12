package com.careerradar.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI drafting endpoints. Every response is a draft; nothing is sent automatically.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "ai", description = "Fit score, cover letter and resume tailoring")
@SecurityRequirement(name = "bearerAuth")
public class AiController {

    private final FitScoreService fitScoreService;
    private final CoverLetterService coverLetterService;
    private final ResumeTailorService tailorService;

    public AiController(
            FitScoreService fitScoreService,
            CoverLetterService coverLetterService,
            ResumeTailorService tailorService) {
        this.fitScoreService = fitScoreService;
        this.coverLetterService = coverLetterService;
        this.tailorService = tailorService;
    }

    @PostMapping("/vacancies/{publicId}/fit")
    @Operation(summary = "Score how well a resume matches a vacancy")
    public FitResponse fit(
            @Parameter(description = "Public vacancy id") @PathVariable UUID publicId,
            @Valid @RequestBody FitRequest request) {
        return fitScoreService.evaluate(publicId, request);
    }

    @PostMapping("/vacancies/{publicId}/cover-letter")
    @Operation(summary = "Draft a cover letter for a vacancy")
    public CoverLetterResponse coverLetter(
            @Parameter(description = "Public vacancy id") @PathVariable UUID publicId,
            @Valid @RequestBody CoverLetterRequest request) {
        return coverLetterService.generate(publicId, request);
    }

    @PostMapping("/resumes/{publicId}/tailor")
    @Operation(summary = "Draft a vacancy-specific version of a resume")
    public TailorResponse tailor(
            @Parameter(description = "Public resume id") @PathVariable UUID publicId,
            @Valid @RequestBody TailorRequest request) {
        return tailorService.tailor(publicId, request);
    }
}
