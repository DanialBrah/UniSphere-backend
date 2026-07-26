package com.unisphere.backend.common.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;
    private Limit auth = new Limit(10, 60);
    private Limit api = new Limit(120, 60);
    private Limit ws = new Limit(60, 60);
    private List<PathOverride> overrides = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Limit {
        private int limit;
        private long windowSeconds = 60;
    }

    @Getter
    @Setter
    public static class PathOverride extends Limit {
        private String pathPattern;
    }
}
