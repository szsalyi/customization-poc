package com.github.szsalyi.customizationpoc.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.forAll;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.responseTimeInMillis;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class CassandraUICustomizationPerformanceTest extends Simulation {

    // Test configuration - same as Oracle for fair comparison
    private static final String BASE_URL = System.getProperty("base.url", "http://localhost:8080");
    private static final int MAX_USERS = Integer.parseInt(System.getProperty("max.users", "2000"));
    private static final int RAMP_DURATION = Integer.parseInt(System.getProperty("ramp.duration", "30"));
    private static final int TEST_DURATION = Integer.parseInt(System.getProperty("test.duration", "180"));

    private static final int TOTAL_USERS = Integer.parseInt(System.getProperty("total.users", "50000"));
    private static final int CONCURRENT_USERS = Integer.parseInt(System.getProperty("concurrent.users", "100"));
    private static final int SETUP_USERS_PER_SEC = Integer.parseInt(System.getProperty("setup.users.per.sec", "100"));
    private static final String DB_TYPE = System.getProperty("db.type", "unknown"); // oracle or cassandra

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .userAgentHeader("Gatling Performance Test - Cassandra")
            .shareConnections()
            .acceptEncodingHeader("gzip, deflate")
            .connectionHeader("keep-alive")
            .maxConnectionsPerHost(20)
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
                    ThreadLocalRandom.current().nextInt(0xFFFFFF),
                    ThreadLocalRandom.current().nextInt(1080)
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

    private static String generateUserId() {
        return "perf_user_" + ThreadLocalRandom.current().nextInt(1, MAX_USERS);
    }

    private static String generateCustomization(int componentCount, String userId) {
        StringBuilder components = new StringBuilder();

        for (int i = 0; i < componentCount; i++) {
            if (i > 0) components.append(",");
            components.append(String.format("""
                            {
                                "componentId": "comp_%d_%d",
                                "componentType": "%s",
                                "name": "Component %d",
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
                    ThreadLocalRandom.current().nextInt(0xFFFFFF),
                    ThreadLocalRandom.current().nextInt(1080)
            ));
        }

        return String.format("""
                {
                    "userId": "%s",
                    "profileName": "Performance Test Profile",
                    "version": "v1",
                    "createdAt": "2025-07-21T09:00:00",
                    "updatedAt": "2025-07-21T09:05:00",
                    "components": [%s]
                }
                """, userId, components);
    }

    // Same helper methods as Oracle
    private static String getRandomComponentType() {
        String[] types = {"HEADER", "SIDEBAR", "FOOTER", "WIDGET", "MENU", "DASHBOARD",
                "CHART", "TABLE", "FORM", "BUTTON", "MODAL", "NAVIGATION"};
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }

    // =============================
    // CASSANDRA PERFORMANCE SCENARIOS
    // =============================

    // Shared user pool - ensures same user IDs across all scenarios
    private static final AtomicInteger sharedUserCounter = new AtomicInteger(0);
    private static final int USER_POOL_SIZE = Math.min(TOTAL_USERS, 10000); // Limit pool size for coordination

    // Feeder that cycles through a consistent user pool
    private static final java.util.Iterator<java.util.Map<String, Object>> coordinatedUserFeeder =
            java.util.stream.Stream.generate(() -> {
                int userId = (sharedUserCounter.incrementAndGet() % USER_POOL_SIZE) + 1;
                return java.util.Map.<String, Object>of(
                        "userId", "user_" + userId,
                        "componentId", "comp_" + userId + "_0" // Predictable first component ID
                );
            }).iterator();

    private static final java.util.Iterator<java.util.Map<String, Object>> userFeeder =
            java.util.stream.Stream.generate(() -> {
                String userId = generateUserId();
                return java.util.Map.<String, Object>of(
                        "userId", userId,
                        "componentId", "comp_" + ThreadLocalRandom.current().nextInt(10000) + "_0"
                );
            }).iterator();

    ScenarioBuilder createOnlyScenario = scenario("Create UI Customization")
            .feed(userFeeder)
            .exec(session -> {
                int componentCount = ThreadLocalRandom.current().nextInt(1, 5);
                String payload = generateCustomization(componentCount, session.getString("userId"));
                return session.set("customizationPayload", payload);
            })
            .exec(http("Create Customization")
                    .post("/api/v1/ui-customization")
                    .body(StringBody("#{customizationPayload}"))
                    .check(status().in(200, 201))
                    .check(responseTimeInMillis().lte(3000))
            )
            .pause(Duration.ofSeconds(2), Duration.ofSeconds(5)); // Longer pause to ensure data is available

    ScenarioBuilder readOnlyScenario = scenario("Read UI Customization")
            .feed(userFeeder)
            .pause(Duration.ofSeconds(2)) // Wait for some data to be created
            .exec(http("Get Customization")
                    .get("/api/v1/ui-customization/#{userId}")
                    .check(status().in(200, 404))
                    .check(responseTimeInMillis().lte(500))
            )
            .pause(Duration.ofMillis(100), Duration.ofMillis(300));

    ScenarioBuilder updateOnlyScenario = scenario("Update UI Customization")
            .feed(userFeeder)
            .pause(Duration.ofSeconds(5)) // Wait longer to ensure data exists
            .exec(session -> session.set("newOrder", ThreadLocalRandom.current().nextInt(1, 100)))
            .exec(http("Update Component Order")
                    .patch("/api/v1/ui-customization/#{userId}/components/#{componentId}/order")
                    .queryParam("newOrder", "#{newOrder}")
                    .check(status().in(200, 404)) // Accept 404 for non-existent data
                    .check(responseTimeInMillis().lte(1000))
            )
            .pause(Duration.ofMillis(200), Duration.ofMillis(600));

    ScenarioBuilder sequentialWorkflowScenario = scenario("Sequential - Full User Journey")
            .feed(coordinatedUserFeeder)
            // Step 1: Create
            .exec(session -> {
                int componentCount = ThreadLocalRandom.current().nextInt(2, 5);
                String payload = generateCustomization(
                        componentCount,
                        session.getString("userId").replace("user_", "")
                );
                return session.set("customizationPayload", payload);
            })
            .exec(http("Create User Customization")
                    .post("/api/v1/ui-customization")
                    .body(StringBody("#{customizationPayload}"))
                    .check(status().in(200, 201, 409))
                    .check(responseTimeInMillis().lte(5000))
                    .check(jsonPath("$.components[0].componentId").optional().saveAs("actualComponentId"))
                    .check(jsonPath("$.id").optional().saveAs("id"))
            )
            .exec(session -> {
                // Extract the ID from the response, with fallback
                String id = session.getString("id");

                return session
                        .set("id", id)
                        .set("hasGeneratedId", id != null)
                        .set("creationTime", System.currentTimeMillis());
            })
            .pause(Duration.ofMillis(500), Duration.ofSeconds(2))

            // Step 2: Read what we just created
            .exec(http("Verify Creation - Read 1")
                    .get("/api/v1/ui-customization/#{userId}")
                    .check(status().in(200, 404))
                    .check(responseTimeInMillis().lte(1000))
            )
            .pause(Duration.ofSeconds(1), Duration.ofSeconds(2))

            // NEW Read 2
            .exec(http("Dashboard Reload - Read 2")
                    .get("/api/v1/ui-customization/#{id}")
                    .check(status().in(200, 404))
                    .check(responseTimeInMillis().lte(1000))
            )
            .pause(Duration.ofMillis(500), Duration.ofSeconds(2))
            // NEW Read 3
            .exec(http("User Profile Check - Read 3")
                    .get("/api/v1/ui-customization/#{id}")
                    .check(status().in(200, 404))
                    .check(responseTimeInMillis().lte(1000))
            )
            .pause(Duration.ofSeconds(1), Duration.ofSeconds(3))
            // Step 3: Update using real component ID if available
            .doIf(session -> session.contains("actualComponentId") && session.contains("id")).then(
                    exec(session -> session.set("newOrder", ThreadLocalRandom.current().nextInt(1, 20)))
                            .exec(http("Update Real Component")
                                    .patch("/api/v1/ui-customization/#{id}/components/#{actualComponentId}/order")
                                    .queryParam("newOrder", "#{newOrder}")
                                    .check(status().in(200, 404))
                                    .check(responseTimeInMillis().lte(2000))
                            )
                            .pause(Duration.ofMillis(200), Duration.ofMillis(800))

                            // Step 4: Final verification
                            .exec(http("Final Verification")
                                    .get("/api/v1/ui-customization/#{userId}")
                                    .check(status().in(200, 404))
                                    .check(responseTimeInMillis().lte(1000))
                            )
            );

    ScenarioBuilder sequentialReadWorkflowScenario = scenario("Sequential - Read User Journey")
            .feed(coordinatedUserFeeder)
            // Step 2: Read what we just created
            .exec(http("Verify Creation")
                    .get("/api/v1/ui-customization/#{id}")
                    .check(status().in(200, 404))
                    .check(responseTimeInMillis().lte(1000))
            )
            .pause(Duration.ofMillis(200), Duration.ofMillis(800));

    //----------OLD VERSION ----------------------------
//    ScenarioBuilder createCustomizationScenario = scenario("Cassandra - Create UI Customization")
//            .exec(session -> {
//                int componentCount = ThreadLocalRandom.current().nextInt(5, 30); // Slightly more components to stress NoSQL
//                String payload = generateCassandraOptimizedCustomization(componentCount);
//                return session.set("customizationPayload", payload)
//                        .set("userId", generateCassandraUserId())
//                        .set("componentCount", componentCount);
//            })
//            .exec(http("Create Customization")
//                    .post("/api/v1/ui-customization")
//                    .body(StringBody("#{customizationPayload}"))
//                    .check(status().in(200, 201))
//                    .check(responseTimeInMillis().lte(3000)) // Expecting better performance
//                    .check(jsonPath("$.userId").exists())
//            )
//            .pause(Duration.ofMillis(50), Duration.ofMillis(300)); // Shorter pauses for higher throughput
//
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


    // ----------- REAL LIFE -----------------

    // =============================
    // PHASE 2: REALISTIC USER BEHAVIOR
    // =============================

//    // 70% of users - Read-heavy behavior (typical app usage)
//    ScenarioBuilder readHeavyUserScenario = scenario("Realistic User - Read Heavy")
//            .exec(session -> {
//                // Pick a random user ID from our setup range
//                int randomUserId = ThreadLocalRandom.current().nextInt(1, TOTAL_USERS + 1);
//                return session.set("userId", "user_" + randomUserId);
//            })
//            // User opens the app - loads their customization
//            .exec(http("Load User Dashboard")
//                    .get("/api/v1/ui-customization/#{userId}")
//                    .check(status().in(200, 404)) // 404 acceptable for some users
//                    .check(responseTimeInMillis().lte(800))
//            )
//            .pause(Duration.ofSeconds(2), Duration.ofSeconds(8)) // User browses for 2-8 seconds
//            // User refreshes or navigates (common behavior)
//            .exec(http("Refresh Dashboard")
//                    .get("/api/v1/ui-customization/#{userId}")
//                    .check(status().in(200, 404))
//                    .check(responseTimeInMillis().lte(500))
//            )
//            .pause(Duration.ofSeconds(5), Duration.ofSeconds(15)) // User stays on page
//
//            // Maybe another read during session
//            .randomSwitch().on(
//                    Choice.withWeight(60,
//                            exec(http("Additional Page Load")
//                                    .get("/api/v1/ui-customization/#{userId}")
//                                    .check(status().in(200, 404))
//                                    .check(responseTimeInMillis().lte(500))
//                            )
//                    )
//            );
//
//    // 25% of users - Read and occasional update behavior
//    ScenarioBuilder normalUserScenario = scenario("Realistic User - Normal Activity")
//            .exec(session -> {
//                int randomUserId = ThreadLocalRandom.current().nextInt(1, TOTAL_USERS + 1);
//                return session.set("userId", "user_" + randomUserId);
//            })
//            // Load initial customization
//            .exec(http("Load Dashboard")
//                    .get("/api/v1/ui-customization/#{userId}")
//                    .check(status().in(200, 404))
//                    .check(responseTimeInMillis().lte(800))
//                    .checkIf(status().is(200).toString())
//                    .then(jsonPath("$.components[?(@.componentType == 'WIDGET')].componentId").findAll().saveAs("widgetIds"))
//            )
//            .pause(Duration.ofSeconds(3), Duration.ofSeconds(10))
//
//            // User might make a small change (reorder components)
//            .doIf(session -> session.contains("widgetIds") && !session.getList("widgetIds").isEmpty())
//            .then(
//                    exec(session -> {
//                        @SuppressWarnings("unchecked")
//                        java.util.List<String> widgetIds = (java.util.List<String>) session.get("widgetIds");
//                        if (!widgetIds.isEmpty()) {
//                            String randomWidget = widgetIds.get(ThreadLocalRandom.current().nextInt(widgetIds.size()));
//                            return session.set("selectedWidget", randomWidget)
//                                    .set("newOrder", ThreadLocalRandom.current().nextInt(1, 20));
//                        }
//                        return session;
//                    })
//                            .exec(http("Reorder Widget")
//                                    .patch("/api/v1/ui-customization/#{userId}/components/#{selectedWidget}/order")
//                                    .queryParam("newOrder", "#{newOrder}")
//                                    .check(status().in(200, 404))
//                                    .check(responseTimeInMillis().lte(1200))
//                            )
//                            .pause(Duration.ofSeconds(1), Duration.ofSeconds(3))
//
//                            // User checks their change
//                            .exec(http("Verify Change")
//                                    .get("/api/v1/ui-customization/#{userId}")
//                                    .check(status().in(200, 404))
//                                    .check(responseTimeInMillis().lte(600))
//                            )
//            );
//
//    // 5% of users - Power users with multiple updates
//    ScenarioBuilder powerUserScenario = scenario("Realistic User - Power User")
//            .exec(session -> {
//                int randomUserId = ThreadLocalRandom.current().nextInt(1, TOTAL_USERS + 1);
//                return session.set("userId", "user_" + randomUserId);
//            })
//            // Power user loads and immediately starts customizing
//            .exec(http("Power User - Load Dashboard")
//                    .get("/api/v1/ui-customization/#{userId}")
//                    .check(status().in(200, 404))
//                    .check(responseTimeInMillis().lte(800))
//            )
//            .pause(Duration.ofSeconds(1), Duration.ofSeconds(3))
//
//            // Multiple updates in sequence (power user behavior)
//            .repeat(3, "updateCounter").on(
//                    doIf(session -> session.contains("allComponentIds") &&
//                            !session.getList("allComponentIds").isEmpty())
//                            .then(
//                                    exec(session -> {
//                                        @SuppressWarnings("unchecked")
//                                        java.util.List<String> componentIds = (java.util.List<String>) session.get("allComponentIds");
//                                        if (!componentIds.isEmpty()) {
//                                            String randomComponent = componentIds.get(ThreadLocalRandom.current().nextInt(componentIds.size()));
//                                            return session.set("selectedComponent", randomComponent)
//                                                    .set("newOrder", ThreadLocalRandom.current().nextInt(1, 50));
//                                        }
//                                        return session;
//                                    })
//                                            .exec(http("Power User - Update #{updateCounter}")
//                                                    .patch("/api/v1/ui-customization/#{userId}/components/#{selectedComponent}/order")
//                                                    .queryParam("newOrder", "#{newOrder}")
//                                                    .check(status().in(200, 404))
//                                                    .check(responseTimeInMillis().lte(1500))
//                                            )
//                                            .pause(Duration.ofMillis(500), Duration.ofSeconds(2))
//                            )
//            )
//
//            // Final verification
//            .exec(http("Power User - Final Check")
//                    .get("/api/v1/ui-customization/#{userId}")
//                    .check(status().in(200, 404))
//                    .check(responseTimeInMillis().lte(600))
//            );


    // =============================
    // TEST EXECUTION PLAN
    // =============================

//    {
//        setUp(
//
//            // PHASE 2: Realistic user simulation with existing data
//            // Wait a bit for setup to complete, then start realistic load
//            readHeavyUserScenario.injectOpen(
//                    rampUsers((int)(CONCURRENT_USERS * 0.7)).during(Duration.ofSeconds(RAMP_DURATION)),
//                    constantUsersPerSec(CONCURRENT_USERS * 0.7 / 10.0).during(Duration.ofSeconds(TEST_DURATION))
//            ),
//
//            normalUserScenario.injectOpen(
//                    nothingFor(Duration.ofSeconds(5)), // Stagger start
//                    rampUsers((int)(CONCURRENT_USERS * 0.25)).during(Duration.ofSeconds(RAMP_DURATION)),
//                    constantUsersPerSec(CONCURRENT_USERS * 0.25 / 10.0).during(Duration.ofSeconds(TEST_DURATION))
//            ),
//
//            powerUserScenario.injectOpen(
//                    nothingFor(Duration.ofSeconds(10)), // Start last
//                    rampUsers((int)(CONCURRENT_USERS * 0.05)).during(Duration.ofSeconds(RAMP_DURATION)),
//                    constantUsersPerSec(CONCURRENT_USERS * 0.05 / 10.0).during(Duration.ofSeconds(TEST_DURATION))
//            )
//
//        ).protocols(httpProtocol)
//                .assertions(
//                        // Setup phase assertions (more lenient)
//                        details("Bulk Setup - Create User Customizations").responseTime().percentile3().lte(8000),
//                        details("Bulk Setup - Create User Customizations").failedRequests().percent().lte(2.0),
//
//                        // Realistic user assertions (stricter)
//                        details("Realistic User - Read Heavy").responseTime().percentile2().lte(600),
//                        details("Realistic User - Normal Activity").responseTime().percentile3().lte(1500),
//                        details("Realistic User - Power User").responseTime().max().lte(3000),
//
//                        // Overall system health
//                        forAll().responseTime().percentile4().lte(5000), // P99.9
//                        forAll().failedRequests().percent().lte(5.0)
//                );
//    }

    // =============================
    // OPTIMIZED LOAD INJECTION (Higher throughput expected)
    // =============================

//    {
//        setUp(
//                createOnlyScenario.injectOpen(
//                        rampUsers(MAX_USERS / 4).during(Duration.ofSeconds(RAMP_DURATION)),
//                        constantUsersPerSec(MAX_USERS / 20.0).during(Duration.ofSeconds(TEST_DURATION))
//                ),
//                readOnlyScenario.injectOpen(
//                        rampUsers(MAX_USERS / 2).during(Duration.ofSeconds(RAMP_DURATION)),
//                        constantUsersPerSec(MAX_USERS / 10.0).during(Duration.ofSeconds(TEST_DURATION))
//                ),
//                updateOnlyScenario.injectOpen(
//                        rampUsers(MAX_USERS / 4).during(Duration.ofSeconds(RAMP_DURATION)),
//                        constantUsersPerSec(MAX_USERS / 20.0).during(Duration.ofSeconds(TEST_DURATION))
//                )
//        ).protocols(httpProtocol)
//                .assertions(
//                        forAll().responseTime().percentile3().lte(2000),
//                        forAll().failedRequests().percent().lte(10.0) // Higher tolerance for 404s
//                );
//    }
//

    // SHARED
    {
        setUp(
                sequentialWorkflowScenario.injectOpen(
                        rampUsers(MAX_USERS).during(Duration.ofSeconds(RAMP_DURATION)),
                        constantUsersPerSec(MAX_USERS / 5.0).during(Duration.ofSeconds(TEST_DURATION))
                )
//                sequentialReadWorkflowScenario.injectOpen(
//                        rampUsers(MAX_USERS).during(Duration.ofSeconds(RAMP_DURATION)),
//                        constantUsersPerSec(MAX_USERS / 50.0).during(Duration.ofSeconds(TEST_DURATION))
//                )

        ).protocols(httpProtocol)
                .assertions(
                        // Database-specific performance expectations
                        forAll().responseTime().percentile3().lte("cassandra".equals(DB_TYPE) ? 1500 : 3000), // P95
                        forAll().failedRequests().percent().lte(8.0),

                        // Specific operation expectations
                        details("Create User Customization").responseTime().percentile2().lte("cassandra".equals(DB_TYPE) ? 300 : 800), // P90
                        details("Verify Creation").responseTime().percentile2().lte("cassandra".equals(DB_TYPE) ? 200 : 500), // P90
                        details("Update Real Component").responseTime().percentile2().lte("cassandra".equals(DB_TYPE) ? 250 : 600) // P90
                );
    }
}
