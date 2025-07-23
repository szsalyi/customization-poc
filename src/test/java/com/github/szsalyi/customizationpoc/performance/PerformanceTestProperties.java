package com.github.szsalyi.customizationpoc.performance;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

@Data
@Builder
public class PerformanceTestProperties {
    private String baseUrl;
    private int maxUsers;
    private int durationSeconds;
    private double targetThroughput;
    private Duration maxResponseTime;
    private double minSuccessRate;
}