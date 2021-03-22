/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

@ExtendWith({WiremockResolver.class})
class HttpCloudFoundryClientTest {
  @Test
  void createRetryInterceptorShouldRetryOnInternalServerErrorsThenTimeOut(
      @WiremockResolver.Wiremock WireMockServer server) throws Exception {
    stubServer(
        server,
        200,
        STARTED,
        "Will respond 502",
        "{\"access_token\":\"token\",\"expires_in\":1000000}");
    stubServer(
        server,
        502,
        "Will respond 502",
        "Will respond 503",
        "{\"errors\":[{\"detail\":\"502 error\"}]}");
    stubServer(
        server,
        503,
        "Will respond 503",
        "Will respond 504",
        "{\"errors\":[{\"detail\":\"503 error\"}]}");
    stubServer(
        server,
        504,
        "Will respond 504",
        "Will respond 200",
        "{\"errors\":[{\"detail\":\"504 error\"}]}");
    stubServer(server, 200, "Will respond 200", "END", "{}");

    HttpCloudFoundryClient cloudFoundryClient = createDefaultCloudFoundryClient(server);

    CloudFoundryApiException thrown =
        assertThrows(
            CloudFoundryApiException.class,
            () -> cloudFoundryClient.getOrganizations().findByName("randomName"),
            "Expected thrown 'Cloud Foundry API returned with error(s): 504 error', but it didn't");

    // 504 means it was retried after 502 and 503
    assertTrue(thrown.getMessage().contains("Cloud Foundry API returned with error(s): 504 error"));
  }

  @Test
  void createRetryInterceptorShouldNotRefreshTokenOnBadCredentials(
      @WiremockResolver.Wiremock WireMockServer server) throws Exception {
    stubServer(server, 401, STARTED, "Bad credentials");

    HttpCloudFoundryClient cloudFoundryClient = createDefaultCloudFoundryClient(server);

    CloudFoundryApiException thrown =
        assertThrows(
            CloudFoundryApiException.class,
            () -> cloudFoundryClient.getOrganizations().findByName("randomName"),
            "Expected thrown 'Cloud Foundry API returned with error(s): Unauthorized', but it didn't");

    assertTrue(thrown.getMessage().contains("Unauthorized"));
  }

  @Test
  void createRetryInterceptorShouldReturnOnSecondAttempt(
      @WiremockResolver.Wiremock WireMockServer server) throws Exception {
    stubServer(
        server,
        200,
        STARTED,
        "Will respond 502",
        "{\"access_token\":\"token\",\"expires_in\":1000000}");
    stubServer(
        server,
        502,
        "Will respond 502",
        "Will respond 200",
        "{\"errors\":[{\"detail\":\"502 error\"}]}");
    stubServer(
        server,
        200,
        "Will respond 200",
        "END",
        "{\"pagination\":{\"total_pages\":1},\"resources\":[{\"guid\": \"orgId\", \"name\":\"orgName\"}]}");

    HttpCloudFoundryClient cloudFoundryClient = createDefaultCloudFoundryClient(server);

    Optional<CloudFoundryOrganization> cloudFoundryOrganization =
        cloudFoundryClient.getOrganizations().findByName("randomName");

    assertThat(cloudFoundryOrganization.get())
        .extracting(CloudFoundryOrganization::getId, CloudFoundryOrganization::getName)
        .containsExactly("orgId", "orgName");
  }

  private void stubServer(
      WireMockServer server, int status, String currentState, String nextState) {
    stubServer(server, status, currentState, nextState, "");
  }

  private void stubServer(
      WireMockServer server, int status, String currentState, String nextState, String body) {
    server.stubFor(
        any(UrlPattern.ANY)
            .inScenario("Retry Scenario")
            .whenScenarioStateIs(currentState)
            .willReturn(
                aResponse()
                    .withStatus(status) // request unsuccessful with status code 500
                    .withHeader("Content-Type", "application/json")
                    .withBody(body))
            .willSetStateTo(nextState));
  }

  @NotNull
  private HttpCloudFoundryClient createDefaultCloudFoundryClient(WireMockServer server) {
    return new HttpCloudFoundryClient(
        "account",
        "appsManUri",
        "metricsUri",
        "localhost:" + server.port() + "/",
        "user",
        "password",
        false,
        true,
        500,
        ForkJoinPool.commonPool(),
        new OkHttpClient.Builder());
  }
}
