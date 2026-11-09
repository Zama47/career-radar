package com.careerradar.resume.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated resume management. The master resume feeds fit-score and cover-letter calls.
 */
@RestController
@RequestMapping("/api/v1/resumes")
@Tag(name = "resumes", description = "Master resume, variants and PDF files")
@SecurityRequirement(name = "bearerAuth")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a resume (optionally the master one)")
    public ResumeDetailResponse create(@Valid @RequestBody ResumeRequest request) {
        return resumeService.create(request);
    }

    @GetMapping
    @Operation(summary = "List resumes, newest first")
    public List<ResumeSummaryResponse> list() {
        return resumeService.list();
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Get a resume with its structured and plain-text content")
    public ResumeDetailResponse get(@PathVariable UUID publicId) {
        return resumeService.get(publicId);
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Update a resume; omitted isMaster keeps the current flag")
    public ResumeDetailResponse update(
            @PathVariable UUID publicId, @Valid @RequestBody ResumeRequest request) {
        return resumeService.update(publicId, request);
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a resume and its PDF")
    public void delete(@PathVariable UUID publicId) {
        resumeService.delete(publicId);
    }

    @GetMapping("/{publicId}/file")
    @Operation(summary = "Download the uploaded PDF")
    public ResponseEntity<Resource> file(
            @Parameter(description = "Public resume id") @PathVariable UUID publicId) {
        ResumeFile file = resumeService.file(publicId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(file.downloadName())
                                .build()
                                .toString())
                .body(file.resource());
    }
}
