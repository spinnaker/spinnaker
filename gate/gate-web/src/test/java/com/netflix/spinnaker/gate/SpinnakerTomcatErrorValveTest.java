/*
 * Copyright 2025 Salesforce, Inc.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import com.netflix.spinnaker.gate.services.PipelineService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Use SpringBootTest.WebEnvironment so tomcat is involved in the test, since the whole point is to
 * test tomcat error handling.
 */
@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:gate-test.yml",
    })
class SpinnakerTomcatErrorValveTest {

  private static final TypeReference<Map<String, Object>> mapType = new TypeReference<>() {};

  @LocalServerPort private int port;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private PipelineService pipelineService;

  /** Mock the application service to disable the background thread that caches applications */
  @MockBean private ApplicationService applicationService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean private DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  /** To prevent periodic calls to clouddriver to query for accounts */
  @MockBean private DefaultProviderLookupService defaultProviderLookupService;

  private static final String APPLICATION = "my-application";

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void testSpinnakerTomcatErrorValve() throws Exception {
    URI uri = new URI("http://localhost:" + port + "/bracket-is-an-invalid-character?[foo]");

    HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

    HttpResponse<String> response = callGate(request, 400);

    Map<String, Object> jsonResponse = objectMapper.readValue(response.body(), mapType);
    assertThat(jsonResponse.get("status")).isEqualTo(400);
    assertThat(jsonResponse.containsKey("error")).isTrue();
    assertThat(jsonResponse.get("error")).isNull();
    assertThat(jsonResponse.get("exception")).isEqualTo(IllegalArgumentException.class.getName());
    assertThat(jsonResponse.get("message"))
        .isEqualTo(
            "Invalid character found in the request target [/bracket-is-an-invalid-character?[foo] ]. The valid characters are defined in RFC 7230 and RFC 3986");
    assertThat(jsonResponse.get("timestamp")).isNotNull();
  }

  @Test
  void invokePipelineConfigPipelineNameHasASlash() throws Exception {
    // arbitrary URL with a slash
    URI uri =
        new URI(
            "http://localhost:"
                + port
                + "/pipelines/"
                + APPLICATION
                + "/pipeline-name/has-a-slash");
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();

    HttpResponse<String> response = callGate(request, 404);
    assertThat(response).isNotNull();

    // Note: the response in this case comes from spring boot's
    // DefaultErrorAttributes, NOT SpinnakerTomcatErrorValve.
    Map<String, Object> jsonResponse = objectMapper.readValue(response.body(), mapType);
    assertThat(jsonResponse.get("status")).isEqualTo(404);
    assertThat(jsonResponse.get("error")).isEqualTo("Not Found");
    assertThat(jsonResponse.containsKey("exception")).isFalse();
    assertThat(jsonResponse.get("message")).isEqualTo("No message available");
    assertThat(jsonResponse.get("timestamp")).isNotNull();

    verify(pipelineService, never()).trigger(anyString(), anyString(), anyMap());
  }

  @Test
  void invokePipelineConfigPipelineNameHasAnEncodedSlash() throws Exception {
    // arbitrary URL with an encoded slash.
    URI uri =
        new URI(
            "http://localhost:"
                + port
                + "/pipelines/my-application/pipeline-name%2fhas-an-encoded-slash");
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();

    HttpResponse<String> response = callGate(request, 400);
    assertThat(response).isNotNull();

    // FIXME: expect a json response
    assertThatThrownBy(() -> objectMapper.readValue(response.body(), mapType))
        .isInstanceOf(JsonParseException.class);

    verify(pipelineService, never()).trigger(anyString(), anyString(), anyMap());
  }

  private HttpResponse<String> callGate(HttpRequest request, int expectedStatusCode)
      throws Exception {
    HttpClient client = HttpClient.newBuilder().build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(expectedStatusCode);

    return response;
  }
}
