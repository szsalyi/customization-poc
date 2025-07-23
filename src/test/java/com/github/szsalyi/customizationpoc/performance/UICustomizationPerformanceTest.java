package com.github.szsalyi.customizationpoc.performance;

import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;


import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.core.OpenInjectionStep.atOnceUsers;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class UICustomizationPerformanceTest extends Simulation {
    HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8080") // Base URL of the Spring Boot application
            .acceptHeader("application/json");

    ScenarioBuilder scn = scenario("Basic Simulation")
            .exec(http("request_hello")
                    .get("/api/hello")
                    .check(status().is(200)));

    {
        setUp(
                scn.injectOpen(atOnceUsers(1000)) // Simulate 1000 users at once
        ).protocols(httpProtocol);
    }
}
