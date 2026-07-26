package com.unisphere.backend.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every other test in this package builds {@link RateLimitProperties} by hand (new + setters) —
 * none of them exercise Spring's actual {@code @ConfigurationProperties} binder against the real
 * keys in application.properties. A typo in a key name, or a mistake in the indexed-list syntax
 * for {@code overrides}, would silently leave a field at its Java default (e.g. limit=0), which
 * would make that endpoint return 429 on every single request in production with no obvious cause.
 * This test binds the exact property keys shipped in application.properties and asserts the
 * resulting values, so that failure mode surfaces here instead of in production.
 */
class RateLimitPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsAllKeys_matchingApplicationPropertiesNamingScheme() {
        contextRunner
                .withPropertyValues(
                        "ratelimit.enabled=true",
                        "ratelimit.auth.limit=10",
                        "ratelimit.auth.window-seconds=60",
                        "ratelimit.api.limit=120",
                        "ratelimit.api.window-seconds=60",
                        "ratelimit.ws.limit=60",
                        "ratelimit.ws.window-seconds=60",
                        "ratelimit.overrides[0].path-pattern=/api/v1/campus/chatbot/**",
                        "ratelimit.overrides[0].limit=20",
                        "ratelimit.overrides[0].window-seconds=60"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RateLimitProperties.class);
                    RateLimitProperties props = context.getBean(RateLimitProperties.class);

                    assertThat(props.isEnabled()).isTrue();
                    assertThat(props.getAuth().getLimit()).isEqualTo(10);
                    assertThat(props.getAuth().getWindowSeconds()).isEqualTo(60);
                    assertThat(props.getApi().getLimit()).isEqualTo(120);
                    assertThat(props.getWs().getLimit()).isEqualTo(60);

                    assertThat(props.getOverrides()).hasSize(1);
                    RateLimitProperties.PathOverride chatbotOverride = props.getOverrides().get(0);
                    assertThat(chatbotOverride.getPathPattern()).isEqualTo("/api/v1/campus/chatbot/**");
                    assertThat(chatbotOverride.getLimit()).isEqualTo(20);
                    assertThat(chatbotOverride.getWindowSeconds()).isEqualTo(60);
                });
    }

    @Test
    void defaultsApply_whenNoPropertiesProvided() {
        contextRunner.run(context -> {
            RateLimitProperties props = context.getBean(RateLimitProperties.class);

            assertThat(props.isEnabled()).isTrue();
            assertThat(props.getAuth().getLimit()).isEqualTo(10);
            assertThat(props.getApi().getLimit()).isEqualTo(120);
            assertThat(props.getWs().getLimit()).isEqualTo(60);
            assertThat(props.getOverrides()).isEmpty();
        });
    }

    @Test
    void ratelimitEnabledFalse_bindsCorrectly_matchingTestProfileOverride() {
        contextRunner
                .withPropertyValues("ratelimit.enabled=false")
                .run(context -> assertThat(context.getBean(RateLimitProperties.class).isEnabled()).isFalse());
    }

    @Configuration
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class TestConfig {
    }
}
