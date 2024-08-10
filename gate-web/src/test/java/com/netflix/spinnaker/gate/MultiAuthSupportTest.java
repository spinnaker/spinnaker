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
package com.netflix.spinnaker.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import com.netflix.spinnaker.gate.services.PipelineService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/**
 * MultiAuthSupport is in gate-core, but is about matching http requests, so use code from gate-web
 * to test it.
 */
@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "spring.security.user.name=testuser",
      "spring.security.user.password=testpassword",
      "security.basicform.enabled=true"
    })
class MultiAuthSupportTest {

  private static final String TEST_USER = "testuser";

  private static final String TEST_PASSWORD = "testpassword";

  @LocalServerPort private int port;

  @Autowired ObjectMapper objectMapper;

  @MockBean PipelineService pipelineService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  /** to prevent period application loading */
  @MockBean ApplicationService applicationService;

  /** To prevent attempts to load accounts */
  @MockBean DefaultProviderLookupService defaultProviderLookupService;

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void handleErrorResponse() throws Exception {
    // given
    String executionId = "12345";
    String expression = "arbitrary";

    // mock an arbitrary endpoint to throw an exception
    doThrow(new IllegalArgumentException("foo"))
        .when(pipelineService)
        .evaluateExpressionForExecution(executionId, expression);

    // when
    String response =
        callGate(
            "http://localhost:"
                + port
                + "/pipelines/"
                + executionId
                + "/evaluateExpression?expression="
                + expression);

    // then
    verify(pipelineService).evaluateExpressionForExecution(executionId, expression);

    assertThat(response).isNotNull();

    // Validate that the response is json.
    JsonNode json = objectMapper.readTree(response);
    assertThat(json).isNotNull();
  }

  /** Generate a request to a gate endpoint that uses authorization and fails. */
  private String callGate(String url) throws Exception {
    HttpClient client = HttpClient.newBuilder().build();

    URI uri = new URI(url);

    String credentials = TEST_USER + ":" + TEST_PASSWORD;
    byte[] encodedCredentials =
        Base64.getEncoder().encode(credentials.getBytes(StandardCharsets.UTF_8));

    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .GET()
            .header("Authorization", "Basic " + new String(encodedCredentials))
            .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    return response.body();
  }
}
