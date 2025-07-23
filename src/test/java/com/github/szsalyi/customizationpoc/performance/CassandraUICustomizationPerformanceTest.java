package com.github.szsalyi.customizationpoc.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.forAll;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.responseTimeInMillis;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

@Slf4j
public class CassandraUICustomizationPerformanceTest extends Simulation {

    // Test configuration - same as Oracle for fair comparison
    private static final String BASE_URL = System.getProperty("base.url", "http://localhost:8080");
    private static final int MAX_USERS = Integer.parseInt(System.getProperty("max.users", "2000"));
    private static final int RAMP_DURATION = Integer.parseInt(System.getProperty("ramp.duration", "300"));
    private static final int TEST_DURATION = Integer.parseInt(System.getProperty("test.duration", "600"));

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .userAgentHeader("Gatling Performance Test - Cassandra")
            .shareConnections()
            .acceptEncodingHeader("gzip, deflate")
            .check(status().not(500), status().not(503));

    // =============================
    // CASSANDRA-OPTIMIZED TEST DATA
    // =============================

    private static String generateCassandraOptimizedCustomization(int componentCount) {
        StringBuilder components = new StringBuilder();

        for (int i = 0; i < componentCount; i++) {
            if (i > 0) components.append(",");
            // Fixed JSON formatting with proper escaping
            components.append(String.format("""
                {
                    "componentId": "cassandra_comp_%d_%d",
                    "componentType": "%s",
                    "name": "Cassandra Component %d",
                    "displayOrder": %d,
                    "visible": true,
                    "properties": {
                        "color": "#%06x",
                        "width": %d
                    },
                    "lastModified": "2025-07-21T09:02:00"
                }
                """,
                    ThreadLocalRandom.current().nextInt(10000), i,
                    getRandomComponentType(),
                    i, i,
                    getRandomTheme(),
                    ThreadLocalRandom.current().nextInt(0xFFFFFF),
                    ThreadLocalRandom.current().nextInt(200, 800),
                    ThreadLocalRandom.current().nextInt(100, 400),
                    ThreadLocalRandom.current().nextInt(1920),
                    ThreadLocalRandom.current().nextInt(1080),
                    ThreadLocalRandom.current().nextBoolean(),
                    generateComplexMetadata(),
                    generateNestedData().replace("\"", "\\\"") // Proper escaping
            ));
        }

        return String.format("""
            {
                "userId": "%s",
                "profileName": "Cassandra Performance Test Profile",
                "version": "v1",
                "createdAt": "2025-07-21T09:00:00",
                "updatedAt": "2025-07-21T09:05:00",
                "components": [%s]
            }
            """, generateCassandraUserId(), components);
    }

    private static String generateCassandraUserId() {
        return "cassandra_perf_user_" + ThreadLocalRandom.current().nextInt(1, MAX_USERS * 2);
    }

    private static String generateComplexMetadata() {
        return String.format("""
            "metadata_timestamp_%d_complex_data_structure_with_nested_elements_%d"
            """, System.currentTimeMillis(), ThreadLocalRandom.current().nextInt(100000));
    }

    private static String generateNestedData() {
        return String.format("""
            {"nested": {"level1": {"level2": {"value": %d, "timestamp": %d}}}}
            """, ThreadLocalRandom.current().nextInt(1000000), System.currentTimeMillis());
    }

    // Same helper methods as Oracle
    private static String getRandomComponentType() {
        String[] types = {"HEADER", "SIDEBAR", "FOOTER", "WIDGET", "MENU", "DASHBOARD",
                "CHART", "TABLE", "FORM", "BUTTON", "MODAL", "NAVIGATION"};
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }

    private static String getRandomTheme() {
        String[] themes = {"dark", "light", "blue", "green", "purple", "orange", "red", "teal"};
        return themes[ThreadLocalRandom.current().nextInt(themes.length)];
    }

    // =============================
    // CASSANDRA PERFORMANCE SCENARIOS
    // =============================

    ScenarioBuilder createCustomizationScenario = scenario("Cassandra - Create UI Customization")
            .exec(session -> {
                int componentCount = ThreadLocalRandom.current().nextInt(5, 30); // Slightly more components to stress NoSQL
                String payload = generateCassandraOptimizedCustomization(componentCount);
                return session.set("customizationPayload", payload)
                        .set("userId", generateCassandraUserId())
                        .set("componentCount", componentCount);
            })
            .exec(http("Create Customization")
                    .post("/api/v1/ui-customization")
                    .body(StringBody("#{customizationPayload}"))
                    .check(status().in(200, 201))
                    .check(responseTimeInMillis().lte(3000)) // Expecting better performance
                    .check(jsonPath("$.userId").exists())
            )
            .pause(Duration.ofMillis(50), Duration.ofMillis(300)); // Shorter pauses for higher throughput

//    ScenarioBuilder readCustomizationScenario = scenario("Cassandra - Read UI Customization")
//            .exec(session -> session.set("userId", generateCassandraUserId()))
//            .exec(http("Get Customization")
//                    .get("/api/v1/ui-customization/#{userId}")
//                    .check(status().in(200, 404))
//                    .check(responseTimeInMillis().lte(500)) // Expecting much faster reads
//            )
//            .pause(Duration.ofMillis(25), Duration.ofMillis(100)); // Very short pauses for read-heavy workload
//
//    ScenarioBuilder updateCustomizationScenario = scenario("Cassandra - Update Component Order")
//            .exec(session -> session.set("userId", generateCassandraUserId())
//                    .set("componentId", "cassandra_comp_" + ThreadLocalRandom.current().nextInt(10000))
//                    .set("newOrder", ThreadLocalRandom.current().nextInt(1, 100)))
//            .exec(http("Update Component Order")
//                    .patch("/api/v1/ui-customization/#{userId}/components/#{componentId}/order")
//                    .queryParam("newOrder", "#{newOrder}")
//                    .check(status().in(200, 404))
//                    .check(responseTimeInMillis().lte(1000)) // Expecting faster updates
//            )
//            .pause(Duration.ofMillis(100), Duration.ofMillis(400));
//
//    ScenarioBuilder toggleVisibilityScenario = scenario("Cassandra - Toggle Component Visibility")
//            .exec(session -> session.set("userId", generateCassandraUserId())
//                    .set("componentId", "cassandra_comp_" + ThreadLocalRandom.current().nextInt(10000)))
//            .exec(http("Toggle Component Visibility")
//                    .patch("/api/v1/ui-customization/#{userId}/components/#{componentId}/visibility")
//                    .check(status().in(200, 404))
//                    .check(responseTimeInMillis().lte(750)) // Expecting fast toggles
//            )
//            .pause(Duration.ofMillis(50), Duration.ofMillis(200));
//
//    // Cassandra excels at bulk operations
//    ScenarioBuilder bulkOperationsScenario = scenario("Cassandra - Bulk Operations")
//            .exec(session -> session.set("userId", generateCassandraUserId()))
//            .repeat(10).on( // More iterations to test Cassandra's write performance
//                    exec(http("Bulk Create Component")
//                            .post("/api/v1/ui-customization")
//                            .body(StringBody(session -> generateCassandraOptimizedCustomization(75))) // Larger payloads
//                            .check(status().in(200, 201, 429))
//                            .check(responseTimeInMillis().lte(5000)) // Expecting better bulk performance
//                    )
//                            .pause(Duration.ofMillis(200))
//            );

    // =============================
    // OPTIMIZED LOAD INJECTION (Higher throughput expected)
    // =============================

    {
        setUp(
                // Higher load for Cassandra since it should handle more
                createCustomizationScenario.injectOpen(
                        rampUsers(MAX_USERS / 3).during(Duration.ofSeconds(RAMP_DURATION)), // 33% creating
                        constantUsersPerSec(MAX_USERS / 6.0).during(Duration.ofSeconds(TEST_DURATION))
                )

//                // Much higher read traffic
//                readCustomizationScenario.injectOpen(
//                        rampUsers(MAX_USERS * 2 / 3).during(Duration.ofSeconds(RAMP_DURATION)), // 67% reading
//                        constantUsersPerSec(MAX_USERS / 3.0).during(Duration.ofSeconds(TEST_DURATION))
//                ),
//
//                // More update traffic
//                updateCustomizationScenario.injectOpen(
//                        rampUsers(MAX_USERS / 6).during(Duration.ofSeconds(RAMP_DURATION)), // 17% updating
//                        constantUsersPerSec(MAX_USERS / 12.0).during(Duration.ofSeconds(TEST_DURATION))
//                ),
//
//                // Higher visibility toggle traffic
//                toggleVisibilityScenario.injectOpen(
//                        rampUsers(MAX_USERS / 4).during(Duration.ofSeconds(RAMP_DURATION)), // 25% toggling
//                        constantUsersPerSec(MAX_USERS / 8.0).during(Duration.ofSeconds(TEST_DURATION))
//                ),
//
//                // More aggressive bulk operations
//                bulkOperationsScenario.injectOpen(
//                        rampUsers(MAX_USERS / 10).during(Duration.ofSeconds(RAMP_DURATION + 30)), // 10% bulk operations
//                        constantUsersPerSec(MAX_USERS / 20.0).during(Duration.ofSeconds(TEST_DURATION - 30))
//                )

        ).protocols(httpProtocol)
                // Higher expectations for Cassandra
                .assertions(
                        // Specific scenario assertions - more stringent
                        forAll().responseTime().percentile3().lte(1500), // All scenarios P95 under 1.5s
                        details("Cassandra - Read UI Customization").responseTime().percentile2().lte(200), // Very fast reads
                        details("Cassandra - Create UI Customization").responseTime().max().lte(4000) // Fast creates
                );
    }
}
