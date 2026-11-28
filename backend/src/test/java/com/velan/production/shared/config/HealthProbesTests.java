package com.velan.production.shared.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class HealthProbesTests {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void livenessAndReadinessReturnUpAndOnlyHealthIsExposed() throws Exception {
        HttpResponse<String> liveness = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health/liveness")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, liveness.statusCode());
        assertTrue(liveness.body().contains("\"UP\""));

        HttpResponse<String> readiness = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health/readiness")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, readiness.statusCode());
        assertTrue(readiness.body().contains("\"UP\""));

        HttpResponse<String> actuatorIndex = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, actuatorIndex.statusCode());
        assertTrue(actuatorIndex.body().contains("\"health\""));
        assertTrue(!actuatorIndex.body().contains("\"env\""));
        assertTrue(!actuatorIndex.body().contains("\"beans\""));
    }
}
