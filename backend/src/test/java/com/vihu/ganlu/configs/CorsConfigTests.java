package com.vihu.ganlu.configs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsConfigTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CorsConfig.class)
            .withPropertyValues(
                    "file.upload-dir=target/test-uploads",
                    "spring.profiles.default=prod");

    @Test
    void applicationStartupFailsClosedForHttpOriginOnItsDefaultProductionPath() {
        contextRunner.withPropertyValues("app.allowed-origins=http://localhost:5173")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    void defaultProductionProfileRejectsHttpOriginsWhenNoProfileIsExplicitlyActive() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setDefaultProfiles("prod");

        assertThrows(IllegalStateException.class,
                () -> configured(environment, "http://localhost:5173").validateOrigins());
    }

    @Test
    void explicitlyActiveDevelopmentProfileCanUseLocalHttpOrigin() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setDefaultProfiles("prod");
        environment.setActiveProfiles("dev");

        assertDoesNotThrow(() -> configured(environment, "http://localhost:5173").validateOrigins());
    }

    @Test
    void productionProfileAcceptsHttpsOrigins() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("prod");

        assertDoesNotThrow(() -> configured(environment, "https://ganlu.example.org").validateOrigins());
    }

    private CorsConfig configured(StandardEnvironment environment, String origins) {
        CorsConfig config = new CorsConfig(environment);
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        ReflectionTestUtils.setField(config, "uploadDir", "target/test-uploads");
        return config;
    }
}
