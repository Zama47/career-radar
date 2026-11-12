package com.careerradar.ai;

import com.careerradar.common.error.NotFoundException;
import com.careerradar.llm.CachedLlmResult;
import com.careerradar.llm.LlmCacheService;
import com.careerradar.llm.LlmInvoker;
import com.careerradar.llm.LlmJsonResult;
import com.careerradar.llm.LlmProperties;
import com.careerradar.llm.LlmTask;
import com.careerradar.llm.PromptTemplates;
import com.careerradar.resume.Resume;
import com.careerradar.resume.ResumeVariantRecorder;
import com.careerradar.resume.api.ResumeService;
import com.careerradar.vacancy.Vacancy;
import com.careerradar.vacancy.VacancyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Tailors the master resume to a vacancy. The result is a draft saved as a variant; the
 * master resume itself is never overwritten.
 */
@Service
public class ResumeTailorService {

    private static final String PROMPT = "resume-tailor.st.md";
    private static final String PROMPT_VERSION = "resume-tailor/v1";
    private static final String SYSTEM_PROMPT = """
            Ты — карьерный ассистент Career Radar. Следуй правилам задания, не выдумывай
            опыт и возвращай строго один JSON-объект без markdown.
            """;
    private static final int MAX_OUTPUT_TOKENS = 4_000;
    private static final String DRAFT_WARNING =
            "Черновик: проверьте факты, даты и технологии перед сохранением.";

    private final ResumeService resumeService;
    private final VacancyRepository vacancyRepository;
    private final LlmProperties properties;
    private final LlmInvoker invoker;
    private final LlmCacheService cache;
    private final PromptTemplates templates;
    private final ObjectMapper objectMapper;
    private final ResumeVariantRecorder variantRecorder;

    public ResumeTailorService(
            ResumeService resumeService,
            VacancyRepository vacancyRepository,
            LlmProperties properties,
            LlmInvoker invoker,
            LlmCacheService cache,
            PromptTemplates templates,
            ObjectMapper objectMapper,
            ResumeVariantRecorder variantRecorder) {
        this.resumeService = resumeService;
        this.vacancyRepository = vacancyRepository;
        this.properties = properties;
        this.invoker = invoker;
        this.cache = cache;
        this.templates = templates;
        this.objectMapper = objectMapper;
        this.variantRecorder = variantRecorder;
    }

    public TailorResponse tailor(UUID resumePublicId, TailorRequest request) {
        Resume resume = resumeService.requireEntity(resumePublicId);
        Vacancy vacancy = vacancyRepository.findByPublicIdWithDetails(request.vacancyId())
                .orElseThrow(() -> new NotFoundException(
                        "Vacancy " + request.vacancyId() + " not found"));
        String resumeText = AiInputs.resumeText(resume);
        String vacancyText = AiInputs.vacancyText(vacancy);
        String resumeJson = AiInputs.resumeJson(writeJson(resume.getContentJson()));
        String model = properties.getWritingModel();

        String cacheKey = LlmCacheService.cacheKey(
                LlmTask.RESUME_TAILOR,
                model,
                PROMPT_VERSION,
                AiInputs.sha256(resumeJson + "|" + resumeText),
                AiInputs.sha256(vacancyText));

        if (!request.isForceRefresh()) {
            var cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                CachedLlmResult hit = cached.get();
                try {
                    TailorPayload payload = parse(hit.result());
                    return response(
                            payload, hit.model(), hit.inputTokens(), hit.outputTokens(), true);
                } catch (RuntimeException ignored) {
                    // Stale or manually corrupted cache entry: regenerate and overwrite below.
                }
            }
        }

        String userPrompt = templates.render(PROMPT, Map.of(
                "resumeJson", resumeJson,
                "resume", resumeText,
                "vacancy", vacancyText));
        LlmJsonResult<TailorPayload> result = invoker.executeJson(
                LlmTask.RESUME_TAILOR,
                model,
                PROMPT_VERSION,
                SYSTEM_PROMPT,
                userPrompt,
                MAX_OUTPUT_TOKENS,
                this::parse);

        variantRecorder.recordTailor(
                resume.getId(), vacancy.getId(), result.value().contentJson());
        cache.put(
                cacheKey,
                LlmTask.RESUME_TAILOR,
                model,
                PROMPT_VERSION,
                new CachedLlmResult(
                        result.raw(),
                        result.response().model(),
                        result.response().inputTokens(),
                        result.response().outputTokens()));

        return response(
                result.value(),
                result.response().model(),
                result.response().inputTokens(),
                result.response().outputTokens(),
                false);
    }

    private TailorPayload parse(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, TailorPayload.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid resume-tailor JSON", ex);
        }
    }

    private String writeJson(Map<String, Object> contentJson) {
        try {
            return objectMapper.writeValueAsString(contentJson == null ? Map.of() : contentJson);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize resume contentJson", ex);
        }
    }

    private static TailorResponse response(
            TailorPayload payload, String model, int inputTokens, int outputTokens, boolean cached) {
        List<String> warnings = new ArrayList<>(payload.warnings());
        if (!warnings.contains(DRAFT_WARNING)) {
            warnings.add(DRAFT_WARNING);
        }
        return new TailorResponse(
                payload.contentJson(),
                payload.diff(),
                warnings,
                model,
                new TokenUsage(inputTokens, outputTokens),
                cached);
    }
}
