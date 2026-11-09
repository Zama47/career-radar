package com.careerradar.ai;

import com.careerradar.common.error.BadRequestException;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Cover-letter drafting. The result is always a draft: nothing is saved to an application or
 * sent by email.
 */
@Service
public class CoverLetterService {

    private static final String PROMPT = "cover-letter.st.md";
    private static final String PROMPT_VERSION = "cover-letter/v1";
    private static final String SYSTEM_PROMPT = """
            Ты — карьерный ассистент Career Radar. Следуй правилам задания, не выдумывай
            факты и возвращай строго один JSON-объект без markdown.
            """;
    private static final int MAX_OUTPUT_TOKENS = 1_200;
    private static final Map<String, String> TONES = Map.of(
            "professional", "профессиональный, без пафоса",
            "friendly", "дружелюбный, но деловой",
            "concise", "краткий и деловой");

    private final ResumeService resumeService;
    private final VacancyRepository vacancyRepository;
    private final LlmProperties properties;
    private final LlmInvoker invoker;
    private final LlmCacheService cache;
    private final PromptTemplates templates;
    private final ObjectMapper objectMapper;
    private final ResumeVariantRecorder variantRecorder;

    public CoverLetterService(
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

    public CoverLetterResponse generate(UUID vacancyPublicId, CoverLetterRequest request) {
        Resume resume = resumeService.requireEntity(request.resumeId());
        Vacancy vacancy = vacancyRepository.findByPublicIdWithDetails(vacancyPublicId)
                .orElseThrow(() -> new NotFoundException(
                        "Vacancy " + vacancyPublicId + " not found"));
        String resumeText = AiInputs.resumeText(resume);
        String vacancyText = AiInputs.vacancyText(vacancy);
        String toneKey = normalizeTone(request.tone());
        String toneInstruction = TONES.get(toneKey);
        String model = properties.getWritingModel();

        String cacheKey = LlmCacheService.cacheKey(
                LlmTask.COVER_LETTER,
                model,
                PROMPT_VERSION,
                AiInputs.sha256(resumeText + "|tone=" + toneKey),
                AiInputs.sha256(vacancyText));

        if (!request.isForceRefresh()) {
            var cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                CachedLlmResult hit = cached.get();
                try {
                    CoverLetterPayload payload = parse(hit.result());
                    return new CoverLetterResponse(
                            payload.subject(),
                            payload.text(),
                            hit.model(),
                            new TokenUsage(hit.inputTokens(), hit.outputTokens()),
                            true);
                } catch (RuntimeException ignored) {
                    // Stale or manually corrupted cache entry: regenerate and overwrite below.
                }
            }
        }

        String userPrompt = templates.render(PROMPT, Map.of(
                "resume", resumeText,
                "vacancy", vacancyText,
                "tone", toneInstruction));
        LlmJsonResult<CoverLetterPayload> result = invoker.executeJson(
                LlmTask.COVER_LETTER,
                model,
                PROMPT_VERSION,
                SYSTEM_PROMPT,
                userPrompt,
                MAX_OUTPUT_TOKENS,
                this::parse);

        variantRecorder.recordCoverLetter(
                resume.getId(),
                vacancy.getId(),
                result.value().text(),
                Map.of("subject", result.value().subject(), "tone", toneKey));
        cache.put(
                cacheKey,
                LlmTask.COVER_LETTER,
                model,
                PROMPT_VERSION,
                new CachedLlmResult(
                        result.raw(),
                        result.response().model(),
                        result.response().inputTokens(),
                        result.response().outputTokens()));

        return new CoverLetterResponse(
                result.value().subject(),
                result.value().text(),
                result.response().model(),
                new TokenUsage(
                        result.response().inputTokens(), result.response().outputTokens()),
                false);
    }

    private CoverLetterPayload parse(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, CoverLetterPayload.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid cover-letter JSON", ex);
        }
    }

    private static String normalizeTone(String tone) {
        String key = tone == null || tone.isBlank()
                ? "professional"
                : tone.trim().toLowerCase(Locale.ROOT);
        if (!TONES.containsKey(key)) {
            throw new BadRequestException(
                    "tone must be one of professional, friendly, concise");
        }
        return key;
    }
}
