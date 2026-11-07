package com.careerradar.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects internal API calls that do not present the shared {@code X-Internal-Token} value.
 * Fails closed when the token is not configured, so a misconfigured deployment never exposes
 * the ingestion endpoint.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private static final Logger log = LoggerFactory.getLogger(InternalTokenFilter.class);

    private final InternalTokenProperties properties;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(InternalTokenProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isConfigured()) {
            log.error("INTERNAL_TOKEN is not configured; refusing internal request {}", request.getRequestURI());
            writeProblem(response, request, HttpStatus.SERVICE_UNAVAILABLE,
                    "Internal API is not configured");
            return;
        }

        String provided = request.getHeader(HEADER);
        boolean matches = provided != null && MessageDigest.isEqual(
                properties.token().getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            log.warn("Rejected internal request {}: missing or invalid {}", request.getRequestURI(), HEADER);
            writeProblem(response, request, HttpStatus.UNAUTHORIZED,
                    "Missing or invalid " + HEADER + " header");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeProblem(
            HttpServletResponse response, HttpServletRequest request, HttpStatus status, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
