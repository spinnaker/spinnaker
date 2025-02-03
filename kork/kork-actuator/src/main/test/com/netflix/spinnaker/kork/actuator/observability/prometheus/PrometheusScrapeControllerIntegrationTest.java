/*
 * Copyright 2025 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.actuator.observability.prometheus;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.restassured.http.ContentType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;

public class PrometheusScrapeControllerIntegrationTest {

  Logger log = LoggerFactory.getLogger(getClass());

  @Rule public MockServerRule mockServerRule = new MockServerRule(this);

  MockServerClient mockServerClient;

  GenericContainer prometheus;

  public void startPrometheus() throws Exception {
    Testcontainers.exposeHostPorts(mockServerRule.getPort());
    var prometheusWaitStrategy =
        new HttpWaitStrategy()
            .forPath("/status")
            .forPort(9090)
            .forStatusCode(200)
            .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES));
    Consumer<OutputFrame> logConsumer =
        (OutputFrame outputFrame) -> log.info(outputFrame.getUtf8String());

    // Create a temp file and inject the free port into the configuration
    var promConfig = Files.createTempFile("prom-config", ".yml");
    var promConfigTemplateResource =
        this.getClass()
            .getClassLoader()
            .getResource("io/armory/plugin/observability/prometheus/prom-config-template.yml");
    var promConfigTemplateContent = Files.readString(Path.of(promConfigTemplateResource.toURI()));
    var processedTemplate =
        promConfigTemplateContent.replace("@@_PORT_@@", String.valueOf(mockServerRule.getPort()));
    Files.writeString(promConfig, processedTemplate, StandardOpenOption.WRITE);
    assertTrue(promConfig.toFile().setReadable(true, false));

    // Start Prometheus
    prometheus =
        new GenericContainer("prom/prometheus:latest")
            .withLogConsumer(logConsumer)
            .withExposedPorts(9090)
            .withCopyFileToContainer(
                MountableFile.forHostPath(promConfig), "/etc/prometheus/prometheus.yml")
            .waitingFor(prometheusWaitStrategy)
            .withStartupTimeout(Duration.ofSeconds(30));
    prometheus.start();
  }

  @After
  public void after() {
    Optional.ofNullable(prometheus).ifPresent(GenericContainer::stop);
  }

  /**
   * https://github.com/armory-plugins/armory-observability-plugin/issues/3 This test ensures we
   * prometheus can consume our expected payload.
   */
  @Test
  public void
      test_that_prometheus_can_scrape_a_payload_with_2_counters_with_that_have_the_same_name_but_different_tags()
          throws Exception {

    startPrometheus();

    var expectedScrapeResource =
        this.getClass()
            .getClassLoader()
            .getResource(
                "io/armory/plugin/observability/prometheus/expected-scrape-with-2-counters-different-tags.txt");

    var expectedContent = Files.readString(Path.of(expectedScrapeResource.toURI()));

    // Stub out a mock server to return our expected Prometheus payload
    mockServerClient
        .when(HttpRequest.request("/prometheus"))
        .respond(
            HttpResponse.response(expectedContent)
                .withHeader("Content-Type", "text/plain;version=0.0.4;charset=utf-8")
                .withStatusCode(200));

    // Make sure that prometheus was able to scrape our mocked expected controller response
    // that has 2 counters with different tag combinations
    do {
      Thread.sleep(1000);
    } while (mockServerClient.retrieveRecordedRequests(HttpRequest.request("/prometheus")).length
        < 1);

    var response =
        given()
            .accept(ContentType.JSON)
            .queryParam("match[]", "foo_total")
            .port(prometheus.getMappedPort(9090))
            .when()
            .get("/api/v1/series")
            .as(PromTsApiResponse.class);

    assertEquals("success", response.getStatus());
    assertEquals(2, response.getData().size());

    // Make sure the ts with labels hostname and optionalExtraMetadata is present with the correct
    // values
    var res =
        response.getData().stream()
            .filter(ts -> ts.containsKey("hostname") && ts.containsKey("optionalExtraMetadata"))
            .collect(Collectors.toList());
    assertEquals(1, res.size());
    var data = res.get(0);
    assertEquals("localhost", data.get("hostname"));
    assertEquals("my-cool-value", data.get("optionalExtraMetadata"));

    // Make sure the ts without the xxx is present with the correct values
    res =
        response.getData().stream()
            .filter(ts -> ts.containsKey("hostname") && !ts.containsKey("optionalExtraMetadata"))
            .collect(Collectors.toList());
    assertEquals(1, res.size());
    data = res.get(0);
    assertEquals("localhost", data.get("hostname"));
    assertNull(data.get("optionalExtraMetadata"));
  }
}
