package com.careerradar.common.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal liveness probe used by the web app and Docker health checks.
 * Business endpoints arrive in later stages.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "system", description = "Service liveness endpoints")
public class PingController {

    @GetMapping("/ping")
    @Operation(summary = "Return service liveness status")
    public PingResponse ping() {
        return new PingResponse("ok");
    }

    public record PingResponse(String status) {
    }
}
