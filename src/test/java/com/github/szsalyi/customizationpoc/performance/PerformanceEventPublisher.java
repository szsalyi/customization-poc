package com.github.szsalyi.customizationpoc.performance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@Slf4j
public class PerformanceEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public PerformanceEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishTestStarted(String testName, String database) {
        PerformanceTestStartedEvent event = new PerformanceTestStartedEvent(
                testName, database, LocalDateTime.now()
        );
        eventPublisher.publishEvent(event);
        log.info("Published test started event: {} for {}", testName, database);
    }

    public void publishTestCompleted(String testName, String database, double throughput, Duration duration) {
        PerformanceTestCompletedEvent event = new PerformanceTestCompletedEvent(
                testName, database, LocalDateTime.now(), throughput, duration
        );
        eventPublisher.publishEvent(event);
        log.info("Published test completed event: {} for {} (throughput: {:.2f})", testName, database, throughput);
    }
}