package com.github.szsalyi.customizationpoc.performance;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class DatabaseMetricsCollector {

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeTests = new AtomicInteger(0);
    private final AtomicReference<Double> finalThroughput = new AtomicReference<>(0.0);

    public DatabaseMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Register common gauges with default values
        Gauge.builder("test.active", activeTests, AtomicInteger::get)
                .description("Number of active performance tests")
                .register(meterRegistry);

        Gauge.builder("test.throughput.final", finalThroughput, AtomicReference::get)
                .description("Final throughput at the end of the test")
                .register(meterRegistry);
    }

    @EventListener
    public void handleTestStarted(PerformanceTestStartedEvent event) {
        log.info("Starting performance test: {}", event.getTestName());
        activeTests.set(1);
    }

    @EventListener
    public void handleTestCompleted(PerformanceTestCompletedEvent event) {
        log.info("Completed performance test: {}", event.getTestName());
        activeTests.set(0);
        finalThroughput.set(event.getFinalThroughput());

        Counter.builder("test.completed.total")
                .tag("database", event.getDatabase())
                .register(meterRegistry)
                .increment();
    }

    public void recordResponseTime(String operation, String database, Duration responseTime, boolean success) {
        Timer.builder("response.time")
                .description("Response time of operations")
                .tag("operation", operation)
                .tag("database", database)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .record(responseTime);
    }

    public void recordThroughput(String database, double operationsPerSecond) {
        Gauge.builder("throughput.current", () -> operationsPerSecond)
                .tag("database", database)
                .register(meterRegistry);
    }

    public void recordDatabaseConnections(String database, int activeConnections, int maxConnections) {
        Gauge.builder("database.connections.active", () -> activeConnections)
                .tag("database", database)
                .register(meterRegistry);

        Gauge.builder("database.connections.max", () -> maxConnections)
                .tag("database", database)
                .register(meterRegistry);
    }

    public void recordMemoryUsage(String database, long usedMemoryMB, long maxMemoryMB) {
        Gauge.builder("memory.used.mb", () -> usedMemoryMB)
                .tag("database", database)
                .register(meterRegistry);

        Gauge.builder("memory.max.mb", () -> maxMemoryMB)
                .tag("database", database)
                .register(meterRegistry);
    }
}