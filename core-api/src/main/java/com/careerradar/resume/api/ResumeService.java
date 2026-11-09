package com.careerradar.resume.api;

import com.careerradar.common.error.NotFoundException;
import com.careerradar.resume.Resume;
import com.careerradar.resume.ResumeRepository;
import com.careerradar.resume.ResumeStorage;
import com.careerradar.resume.ResumeStorageProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resume CRUD. PDF bytes are stored locally; the database keeps only a generated filename.
 */
@Service
public class ResumeService {

    private final ResumeRepository repository;
    private final ResumeStorage storage;
    private final ResumeStorageProperties storageProperties;

    public ResumeService(
            ResumeRepository repository,
            ResumeStorage storage,
            ResumeStorageProperties storageProperties) {
        this.repository = repository;
        this.storage = storage;
        this.storageProperties = storageProperties;
    }

    @Transactional(readOnly = true)
    public List<ResumeSummaryResponse> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream()
                .map(ResumeService::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumeDetailResponse get(UUID publicId) {
        return toDetail(requireEntity(publicId));
    }

    @Transactional
    public ResumeDetailResponse create(ResumeRequest request) {
        Resume resume = new Resume(request.title().trim(), request.contentJson(), request.contentText());
        if (Boolean.TRUE.equals(request.isMaster())) {
            repository.clearMasterFlag();
            resume.setMaster(true);
        }
        storePdf(resume, request.pdfBase64());
        repository.save(resume);
        return toDetail(resume);
    }

    @Transactional
    public ResumeDetailResponse update(UUID publicId, ResumeRequest request) {
        Resume resume = requireEntity(publicId);
        resume.setTitle(request.title().trim());
        resume.setContentJson(request.contentJson());
        resume.setContentText(request.contentText());
        if (request.isMaster() != null) {
            if (request.isMaster()) {
                repository.clearOtherMasterFlags(resume.getId());
            }
            resume.setMaster(request.isMaster());
        }
        storePdf(resume, request.pdfBase64());
        return toDetail(resume);
    }

    @Transactional
    public void delete(UUID publicId) {
        Resume resume = requireEntity(publicId);
        String pdfPath = resume.getPdfPath();
        repository.delete(resume);
        repository.flush();
        storage.delete(pdfPath);
    }

    @Transactional(readOnly = true)
    public ResumeFile file(UUID publicId) {
        Resume resume = requireEntity(publicId);
        if (resume.getPdfPath() == null) {
            throw new NotFoundException("Resume " + publicId + " has no PDF");
        }
        return new ResumeFile(storage.load(resume.getPdfPath()), publicId + ".pdf");
    }

    /**
     * Shared lookup for AI services. Returns the entity inside the caller's transaction.
     */
    @Transactional(readOnly = true)
    public Resume requireEntity(UUID publicId) {
        return repository.findByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Resume " + publicId + " not found"));
    }

    private void storePdf(Resume resume, String pdfBase64) {
        if (pdfBase64 == null || pdfBase64.isBlank()) {
            return;
        }
        byte[] bytes = PdfDecoder.decode(pdfBase64, storageProperties.getMaxBytes());
        resume.setPdfPath(storage.store(resume.getPublicId(), bytes));
    }

    private static ResumeSummaryResponse toSummary(Resume resume) {
        return new ResumeSummaryResponse(
                resume.getPublicId(),
                resume.getTitle(),
                resume.isMaster(),
                resume.getPdfPath() != null,
                resume.getCreatedAt(),
                resume.getUpdatedAt());
    }

    private static ResumeDetailResponse toDetail(Resume resume) {
        return new ResumeDetailResponse(
                resume.getPublicId(),
                resume.getTitle(),
                resume.getContentJson(),
                resume.getContentText(),
                resume.isMaster(),
                resume.getPdfPath() != null,
                resume.getCreatedAt(),
                resume.getUpdatedAt());
    }
}
