package com.github.szsalyi.customizationpoc.performance;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PerformanceTestCompletedEvent {
    private String testName;
    private String database;
    private LocalDateTime endTime;
    private double finalThroughput;
    private Duration totalDuration;
}