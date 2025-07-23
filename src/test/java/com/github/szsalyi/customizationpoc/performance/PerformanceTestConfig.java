package com.github.szsalyi.customizationpoc.performance;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("performance")
public class PerformanceTestConfig {

    @Bean
    @Primary
    public MeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    @Bean
    public DatabaseMetricsCollector databaseMetricsCollector(MeterRegistry meterRegistry) {
        return new DatabaseMetricsCollector(meterRegistry);
    }

    @Value("${performance.test.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${performance.test.max-users:1000}")
    private int maxUsers;

    @Value("${performance.test.duration:300}")
    private int durationSeconds;

    @Bean
    public PerformanceTestProperties performanceTestProperties() {
        return PerformanceTestProperties.builder()
                .baseUrl(baseUrl)
                .maxUsers(maxUsers)
                .durationSeconds(durationSeconds)
                .build();
    }
}