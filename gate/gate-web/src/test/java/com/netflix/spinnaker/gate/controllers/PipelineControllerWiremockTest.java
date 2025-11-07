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
package com.netflix.spinnaker.gate.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.netflix.spinnaker.kork.common.Header.ACCOUNTS;
import static com.netflix.spinnaker.kork.common.Header.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * There are tests here for PipelineController methods that call orca, using a wiremock for orca.
 * InvokePipelineConfigTest is separate since it also has a wiremock for front50.
 *
 * <p>Use webEnvironment and make requests with a "plain" http client (i.e. not MockMvc) to be able
 * response bodies from exception handling. See
 * https://github.com/spring-projects/spring-boot/issues/5574#issuecomment-506282892 for background.
 */
@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"spring.config.location=classpath:gate-test.yml"})
class PipelineControllerWiremockTest {

  @RegisterExtension
  static WireMockExtension wmOrca =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @LocalServerPort private int port;

  @Autowired ObjectMapper objectMapper;

  /**
   * To prevent refreshing the applications cache, which involves calls to clouddriver and front50.
   */
  @MockBean ApplicationService applicationService;

  /** To prevent calls to clouddriver */
  @MockBean DefaultProviderLookupService defaultProviderLookupService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private static final String USERNAME = "some user";
  private static final String ACCOUNT = "my-account";
  private static final String PIPELINE_EXECUTION_ID = "my-pipeline-execution-id";
  private static final String PIPELINE_EXECUTION_STATUS = "SUCCEEDED"; // arbitrary
  private static final String READ_REPLICA_REQUIREMENT = "UP_TO_DATE"; // arbitrary

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    // Configure wiremock's random port into gate
    System.out.println("wiremock orca url: " + wmOrca.baseUrl());
    registry.add("services.orca.base-url", wmOrca::baseUrl);
  }

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @ParameterizedTest(name = "getPipelineStatusSuccess includeContentType = {0}")
  @ValueSource(booleans = {true, false})
  void getPipelineStatusSuccess(boolean includeContentType) throws Exception {
    simulateOrcaPipelineStatusSuccess(PIPELINE_EXECUTION_STATUS, includeContentType);

    String response = callGate(getPipelineStatusUrl(PIPELINE_EXECUTION_ID));

    assertThat(response).isEqualTo(PIPELINE_EXECUTION_STATUS);

    verifyOrcaGetPipelineStatus();
  }

  @Test
  void getPipelineStatusBadHttpResponse() throws Exception {
    // simulate a non-200 http response to verify exception handling behavior
    int statusCode = 400;
    String error = "Bad Request";
    String message =
        "No enum constant com.netflix.spinnaker.orca.pipeline.persistence.ReadReplicaRequirement.FOO";
    Map<String, Object> illegalArgumentExceptionResponse =
        Map.of(
            "timestamp",
            1725676651652L,
            "status",
            statusCode,
            "error",
            error,
            "exception",
            "java.lang.IllegalArgumentException",
            "message",
            message);
    String illegalArgumentExceptionResponseJson =
        objectMapper.writeValueAsString(illegalArgumentExceptionResponse);

    simulateOrcaPipelineStatusResponse(
        aResponse()
            .withStatus(HttpStatus.BAD_REQUEST.value())
            .withBody(illegalArgumentExceptionResponseJson));

    String url = getPipelineStatusUrl(PIPELINE_EXECUTION_ID);

    String response = callGate(url);

    // Expect a json response in case of an exception
    Map<String, Object> responseMap = objectMapper.readValue(response, MAP_TYPE);

    assertThat(responseMap)
        .contains(
            entry("status", statusCode),
            entry("error", error),
            entry(
                "exception",
                "com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException"));
    assertThat(responseMap).containsKey("message");
    assertThat(responseMap.get("message")).isInstanceOf(String.class);
    assertThat(responseMap.get("message").toString()).contains(message);

    verifyOrcaGetPipelineStatus();
  }

  @Test
  void getPipelineStatusFault() throws Exception {
    // simulate an arbitrary fault to verify exception handling behavior
    simulateOrcaPipelineStatusResponse(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

    String response = callGate(getPipelineStatusUrl(PIPELINE_EXECUTION_ID));

    // Expect a json response in case of an exception
    Map<String, Object> responseMap = objectMapper.readValue(response, MAP_TYPE);

    assertThat(responseMap)
        .contains(
            entry("status", 500),
            entry("error", "Internal Server Error"),
            entry(
                "exception",
                "com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException"),
            entry("message", "java.net.SocketException: Connection reset"));

    verifyOrcaGetPipelineStatus();
  }

  private void simulateOrcaPipelineStatusSuccess(
      String executionStatus, boolean includeContentTypeHeader) {
    // orca includes a Content-Type: text/plain;charset-UTF=8 header, but gate
    // still processes the response body as a plain string without it.
    ResponseDefinitionBuilder responseDefinitionBuilder =
        aResponse().withStatus(HttpStatus.OK.value()).withBody(executionStatus);
    if (includeContentTypeHeader) {
      responseDefinitionBuilder.withHeader("Content-Type", "text/plain;charset=UTF-8");
    }
    simulateOrcaPipelineStatusResponse(responseDefinitionBuilder);
  }

  private void simulateOrcaPipelineStatusResponse(
      ResponseDefinitionBuilder responseDefinitionBuilder) {
    wmOrca.stubFor(
        WireMock.get(urlPathEqualTo("/pipelines/" + PIPELINE_EXECUTION_ID + "/status"))
            .withQueryParam("readReplicaRequirement", equalTo(READ_REPLICA_REQUIREMENT))
            .willReturn(responseDefinitionBuilder));
  }

  private String getPipelineStatusUrl(String executionId) {
    return "http://localhost:" + port + "/pipelines/" + PIPELINE_EXECUTION_ID + "/status";
  }
  /** Generate a get request to a gate endpoint and return the response */
  private String callGate(String url) throws Exception {
    HttpClient client = HttpClient.newBuilder().build();

    URI uri = new URI(url);

    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .GET()
            .header(
                USER.getHeader(), USERNAME) // to silence warning when X-SPINNAKER-USER is missing
            .header(
                ACCOUNTS.getHeader(),
                ACCOUNT) // to silence warning when X-SPINNAKER-ACCOUNTS is missing
            .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    return response.body();
  }

  private void verifyOrcaGetPipelineStatus() {
    wmOrca.verify(
        getRequestedFor(urlPathEqualTo("/pipelines/" + PIPELINE_EXECUTION_ID + "/status"))
            .withQueryParam("readReplicaRequirement", equalTo(READ_REPLICA_REQUIREMENT)));
  }
}
