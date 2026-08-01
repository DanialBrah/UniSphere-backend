package com.unisphere.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    /** Commit the running build came from — see app.git-sha in application.properties. */
    private final String gitSha;

    public HealthController(@Value("${app.git-sha}") String gitSha) {
        this.gitSha = gitSha;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "UniSphere Backend",
                // Short form — enough to identify the deploy, no more than needed on an
                // unauthenticated endpoint.
                "version", gitSha.length() > 7 ? gitSha.substring(0, 7) : gitSha);
    }
}
