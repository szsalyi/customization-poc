package com.github.szsalyi.customizationpoc.performance;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PerformanceTestStartedEvent {
    private String testName;
    private String database;
    private LocalDateTime startTime;
}
