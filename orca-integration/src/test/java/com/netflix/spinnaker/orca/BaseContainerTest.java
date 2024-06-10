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
package com.netflix.spinnaker.orca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class BaseContainerTest {

  private static final Logger logger = LoggerFactory.getLogger(BaseContainerTest.class);

  protected final Network network = Network.newNetwork();

  private static final int ORCA_PORT = 8083;

  protected GenericContainer<?> orcaContainer;

  private static DockerImageName dockerImageName;

  @BeforeAll
  static void setupInit() {
    String fullDockerImageName = System.getenv("FULL_DOCKER_IMAGE_NAME");
    // Skip the tests if there's no docker image.  This allows gradlew build to work.
    assumeTrue(fullDockerImageName != null);
    dockerImageName = DockerImageName.parse(fullDockerImageName);
  }

  @BeforeEach
  void init(TestInfo testInfo) throws Exception {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
    orcaContainer =
        new GenericContainer(dockerImageName)
            .withNetwork(network)
            .withExposedPorts(ORCA_PORT)
            .waitingFor(Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(120)));
  }

  void testHealthCheck() throws Exception {
    // hit an arbitrary endpoint
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "http://"
                        + orcaContainer.getHost()
                        + ":"
                        + orcaContainer.getFirstMappedPort()
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
