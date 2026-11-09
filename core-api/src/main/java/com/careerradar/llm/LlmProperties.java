package com.careerradar.llm;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenCode Go (OpenAI-compatible) settings. Prices are configured per model and default to
 * zero: OpenCode Go does not publish per-token prices, but token counts are always recorded.
 */
@ConfigurationProperties(prefix = "career-radar.llm")
public class LlmProperties {

    private String baseUrl = "";
    private String apiKey = "";
    private String defaultModel = "deepseek-v4.1-flash";
    private String writingModel = "deepseek-v4-pro";
    private String classificationModel = "claude-haiku-5-5";
    private int timeoutSeconds = 60;
    private int maxRetries = 2;
    private int cacheTtlHours = 168;
    private List<ModelPrice> pricing = new ArrayList<>();

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    public ModelPrice priceFor(String model) {
        if (model == null) {
            return ModelPrice.free();
        }
        return pricing.stream()
                .filter(price -> model.equalsIgnoreCase(price.getModel()))
                .findFirst()
                .orElseGet(ModelPrice::free);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public String getWritingModel() {
        return writingModel;
    }

    public void setWritingModel(String writingModel) {
        this.writingModel = writingModel;
    }

    public String getClassificationModel() {
        return classificationModel;
    }

    public void setClassificationModel(String classificationModel) {
        this.classificationModel = classificationModel;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getCacheTtlHours() {
        return cacheTtlHours;
    }

    public void setCacheTtlHours(int cacheTtlHours) {
        this.cacheTtlHours = cacheTtlHours;
    }

    public List<ModelPrice> getPricing() {
        return pricing;
    }

    public void setPricing(List<ModelPrice> pricing) {
        this.pricing = pricing == null ? new ArrayList<>() : pricing;
    }

    public static class ModelPrice {

        private String model;
        private BigDecimal inputPerMillion = BigDecimal.ZERO;
        private BigDecimal outputPerMillion = BigDecimal.ZERO;

        static ModelPrice free() {
            return new ModelPrice();
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public BigDecimal getInputPerMillion() {
            return inputPerMillion == null ? BigDecimal.ZERO : inputPerMillion;
        }

        public void setInputPerMillion(BigDecimal inputPerMillion) {
            this.inputPerMillion = inputPerMillion;
        }

        public BigDecimal getOutputPerMillion() {
            return outputPerMillion == null ? BigDecimal.ZERO : outputPerMillion;
        }

        public void setOutputPerMillion(BigDecimal outputPerMillion) {
            this.outputPerMillion = outputPerMillion;
        }
    }
}
