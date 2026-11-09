package com.careerradar.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OpenAiCompatibleLlmClientTest {

    private WireMockServer server;
    private LlmProperties properties;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        properties = new LlmProperties();
        properties.setBaseUrl(server.baseUrl());
        properties.setApiKey("test-key");
        properties.setTimeoutSeconds(5);
        properties.setMaxRetries(0);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void parsesContentUsageAndModel() {
        server.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"model":"test-model",
                                 "choices":[{"message":{"role":"assistant","content":"{\\"score\\": 78}"}}],
                                 "usage":{"prompt_tokens":120,"completion_tokens":30}}
                                """)));

        LlmResponse response = client().complete(
                new LlmRequest("test-model", "system", "user", 800));

        assertThat(response.text()).isEqualTo("{\"score\": 78}");
        assertThat(response.model()).isEqualTo("test-model");
        assertThat(response.inputTokens()).isEqualTo(120);
        assertThat(response.outputTokens()).isEqualTo(30);
        assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0);

        server.verify(postRequestedFor(urlEqualTo("/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("test-model")))
                .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("800"))));
    }

    @Test
    void retriesServerErrorsThenSucceeds() {
        properties.setMaxRetries(1);
        server.stubFor(post(urlEqualTo("/chat/completions"))
                .inScenario("retry")
                .whenScenarioStateIs("Started")
                .willSetStateTo("ok")
                .willReturn(aResponse().withStatus(500)));
        server.stubFor(post(urlEqualTo("/chat/completions"))
                .inScenario("retry")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"model":"test-model","choices":[{"message":{"content":"{}"}}],
                                 "usage":{"prompt_tokens":1,"completion_tokens":1}}
                                """)));

        LlmResponse response = client().complete(
                new LlmRequest("test-model", "system", "user", 100));

        assertThat(response.text()).isEqualTo("{}");
        server.verify(2, postRequestedFor(urlEqualTo("/chat/completions")));
    }

    @Test
    void doesNotRetryClientErrors() {
        properties.setMaxRetries(2);
        server.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> client().complete(
                        new LlmRequest("test-model", "system", "user", 100)))
                .isInstanceOf(LlmException.class)
                .satisfies(error -> assertThat(((LlmException) error).getStatus())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));

        server.verify(1, postRequestedFor(urlEqualTo("/chat/completions")));
    }

    @Test
    void reportsMissingConfigurationWithoutCallingProvider() {
        LlmProperties unconfigured = new LlmProperties();
        LlmClient client = new OpenAiCompatibleLlmClient(unconfigured, new ObjectMapper());

        assertThatThrownBy(() -> client.complete(
                        new LlmRequest("test-model", "system", "user", 100)))
                .isInstanceOf(LlmException.class)
                .satisfies(error -> assertThat(((LlmException) error).getStatus())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private LlmClient client() {
        return new OpenAiCompatibleLlmClient(properties, new ObjectMapper());
    }
}
