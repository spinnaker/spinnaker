/*
 * Copyright 2024 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.fiat;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class StandaloneContainerTest {

  private static final String REDIS_NETWORK_ALIAS = "redisHost";

  private static final int REDIS_PORT = 6379;

  private static final Logger logger = LoggerFactory.getLogger(StandaloneContainerTest.class);

  private static final Network network = Network.newNetwork();

  // fiat gets accounts and applications from clouddriver, and fiat's health
  // depends on that succeeding.
  @RegisterExtension
  static final WireMockExtension wmClouddriver =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  // fiat gets service accounts and applications from front50, and fiat's health
  // depends on that succeeding.
  @RegisterExtension
  static final WireMockExtension wmFront50 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static int clouddriverPort;
  static int front50Port;

  private static final GenericContainer redis =
      new GenericContainer(DockerImageName.parse("library/redis:5-alpine"))
          .withNetwork(network)
          .withNetworkAliases(REDIS_NETWORK_ALIAS)
          .withExposedPorts(REDIS_PORT);

  private static GenericContainer fiatContainer;

  @BeforeAll
  static void setupOnce() throws Exception {
    front50Port = wmFront50.getRuntimeInfo().getHttpPort();
    logger.info("wiremock front50 http port: {} ", front50Port);

    clouddriverPort = wmClouddriver.getRuntimeInfo().getHttpPort();
    logger.info("wiremock clouddriver http port: {} ", clouddriverPort);

    // set up front50 stubs
    wmFront50.stubFor(
        WireMock.get(urlPathEqualTo("/v2/applications"))
            .willReturn(aResponse().withStatus(200).withBody("[]")));

    wmFront50.stubFor(
        WireMock.get(urlPathEqualTo("/serviceAccounts"))
            .willReturn(aResponse().withStatus(200).withBody("[]")));

    // set up clouddriver stubs
    wmClouddriver.stubFor(
        WireMock.get(urlPathEqualTo("/applications"))
            .willReturn(aResponse().withStatus(200).withBody("[]")));

    wmClouddriver.stubFor(
        WireMock.get(urlPathEqualTo("/credentials"))
            .willReturn(aResponse().withStatus(200).withBody("[]")));

    String fullDockerImageName = System.getenv("FULL_DOCKER_IMAGE_NAME");

    // Skip the tests if there's no docker image.  This allows gradlew build to work.
    assumeTrue(fullDockerImageName != null);

    // expose front50 to fiat
    org.testcontainers.Testcontainers.exposeHostPorts(front50Port);

    // expose clouddriver to fiat
    org.testcontainers.Testcontainers.exposeHostPorts(clouddriverPort);

    redis.start();

    DockerImageName dockerImageName = DockerImageName.parse(fullDockerImageName);

    fiatContainer =
        new GenericContainer(dockerImageName)
            .withNetwork(network)
            .withExposedPorts(7003)
            .dependsOn(redis)
            .waitingFor(Wait.forHealthcheck())
            .withEnv("SPRING_APPLICATION_JSON", getSpringApplicationJson());

    Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(logger);
    fiatContainer.start();
    fiatContainer.followOutput(logConsumer);
  }

  private static String getSpringApplicationJson() throws JsonProcessingException {
    String redisUrl = "redis://" + REDIS_NETWORK_ALIAS + ":" + REDIS_PORT;
    logger.info("redisUrl: '{}'", redisUrl);
    Map<String, String> properties =
        Map.of(
            "redis.connection",
            redisUrl,
            "services.igor.baseUrl",
            "http://nowhere",
            "services.clouddriver.baseUrl",
            "http://" + GenericContainer.INTERNAL_HOST_HOSTNAME + ":" + clouddriverPort,
            "services.front50.baseUrl",
            "http://" + GenericContainer.INTERNAL_HOST_HOSTNAME + ":" + front50Port);
    ObjectMapper mapper = new ObjectMapper();
    return mapper.writeValueAsString(properties);
  }

  @AfterAll
  static void cleanupOnce() {
    if (fiatContainer != null) {
      fiatContainer.stop();
    }

    if (redis != null) {
      redis.stop();
    }
  }

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void testHealthCheck() throws Exception {
    // hit an arbitrary endpoint
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "http://"
                        + fiatContainer.getHost()
                        + ":"
                        + fiatContainer.getFirstMappedPort()
                        + "/health"))
            .GET()
            .build();

    HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response).isNotNull();
    logger.info("response: {}, {}", response.statusCode(), response.body());
    assertThat(response.statusCode()).isEqualTo(200);
  }
}
