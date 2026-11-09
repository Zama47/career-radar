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
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Fit-score scenario: one structured LLM call, a cached result for seven days and a
 * {@code resume_variants} row. No vacancy or resume data leaves the app except to the LLM.
 */
@Service
public class FitScoreService {

    private static final String PROMPT = "fit-score.st.md";
    private static final String PROMPT_VERSION = "fit-score/v1";
    private static final String SYSTEM_PROMPT = """
            Ты — карьерный ассистент Career Radar. Следуй правилам задания, используй только
            факты из резюме и вакансии и возвращай строго один JSON-объект без markdown.
            """;
    private static final int MAX_OUTPUT_TOKENS = 800;

    private final ResumeService resumeService;
    private final VacancyRepository vacancyRepository;
    private final LlmProperties properties;
    private final LlmInvoker invoker;
    private final LlmCacheService cache;
    private final PromptTemplates templates;
    private final ObjectMapper objectMapper;
    private final ResumeVariantRecorder variantRecorder;

    public FitScoreService(
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

    public FitResponse evaluate(UUID vacancyPublicId, FitRequest request) {
        Resume resume = resumeService.requireEntity(request.resumeId());
        Vacancy vacancy = requireVacancy(vacancyPublicId);
        String resumeText = AiInputs.resumeText(resume);
        String vacancyText = AiInputs.vacancyText(vacancy);
        String model = properties.getDefaultModel();

        String cacheKey = LlmCacheService.cacheKey(
                LlmTask.FIT_SCORE,
                model,
                PROMPT_VERSION,
                AiInputs.sha256(resumeText),
                AiInputs.sha256(vacancyText));

        if (!request.isForceRefresh()) {
            var cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                CachedLlmResult hit = cached.get();
                try {
                    FitPayload payload = parse(hit.result());
                    return response(
                            payload, hit.model(), hit.inputTokens(), hit.outputTokens(), true);
                } catch (RuntimeException ignored) {
                    // Stale or manually corrupted cache entry: regenerate and overwrite below.
                }
            }
        }

        String userPrompt = templates.render(PROMPT, Map.of(
                "resume", resumeText,
                "vacancy", vacancyText));
        LlmJsonResult<FitPayload> result = invoker.executeJson(
                LlmTask.FIT_SCORE,
                model,
                PROMPT_VERSION,
                SYSTEM_PROMPT,
                userPrompt,
                MAX_OUTPUT_TOKENS,
                this::parse);

        variantRecorder.recordFit(
                resume.getId(), vacancy.getId(), result.value().score(), toMap(result.raw()));
        cache.put(
                cacheKey,
                LlmTask.FIT_SCORE,
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

    private Vacancy requireVacancy(UUID publicId) {
        return vacancyRepository.findByPublicIdWithDetails(publicId)
                .orElseThrow(() -> new NotFoundException("Vacancy " + publicId + " not found"));
    }

    private FitPayload parse(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, FitPayload.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid fit-score JSON", ex);
        }
    }

    private Map<String, Object> toMap(JsonNode node) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = objectMapper.convertValue(node, Map.class);
        return map;
    }

    private static FitResponse response(
            FitPayload payload, String model, int inputTokens, int outputTokens, boolean cached) {
        return new FitResponse(
                payload.score(),
                payload.verdict(),
                payload.strengths(),
                payload.missing(),
                payload.reasons(),
                model,
                new TokenUsage(inputTokens, outputTokens),
                cached);
    }
}
