/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Captures outbound Compute HTTP requests (method, URL, JSON body) for API-boundary contract tests.
 * Public for reuse across handler and load-balancer operation test packages.
 */
public final class CapturingComputeTransport extends HttpTransport {
  private static final Pattern LIST_RESOURCE_PATTERN =
      Pattern.compile(
          "/(healthChecks|backendServices|forwardingRules|urlMaps|targetHttpProxies|targetHttpsProxies)(?:\\?.*)?$");
  private static final String NOT_FOUND_JSON =
      "{\"error\":{\"code\":404,\"message\":\"not found\"}}";

  private final List<CapturedRequest> requests = new ArrayList<>();
  private final String operationResponseJson;
  private final Map<String, String> getResponsesByPathSubstring = new HashMap<>();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public CapturingComputeTransport() {
    this(
        "{"
            + "\"name\":\"operation-1\","
            + "\"targetLink\":\"https://www.googleapis.com/compute/v1/projects/test-project/regions/us-central1/operations/operation-1\","
            + "\"status\":\"DONE\""
            + "}");
  }

  public CapturingComputeTransport(String operationResponseJson) {
    this.operationResponseJson = operationResponseJson;
  }

  public CapturingComputeTransport registerGetResponse(String pathSubstring, String jsonBody) {
    getResponsesByPathSubstring.put(pathSubstring, jsonBody);
    return this;
  }

  @Override
  public boolean supportsMethod(String method) {
    return true;
  }

  @Override
  protected LowLevelHttpRequest buildRequest(String method, String url) {
    return new MockLowLevelHttpRequest(url) {
      @Override
      public MockLowLevelHttpResponse execute() throws IOException {
        String body = getContentAsString();
        requests.add(new CapturedRequest(method, url, body != null ? body : ""));
        return buildResponse(method, url, body);
      }
    };
  }

  private MockLowLevelHttpResponse buildResponse(String method, String url, String requestBody)
      throws IOException {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    if ("GET".equals(method)) {
      for (Map.Entry<String, String> entry : getResponsesByPathSubstring.entrySet()) {
        if (url.contains(entry.getKey())) {
          return response.setStatusCode(200).setContent(entry.getValue());
        }
      }
      if (url.endsWith("/regions") || url.contains("/regions?")) {
        return response.setStatusCode(200).setContent("{\"items\":[{\"name\":\"us-central1\"}]}");
      }
      if (url.contains("/operations/")) {
        return response.setStatusCode(200).setContent(operationResponseJson);
      }
      if (LIST_RESOURCE_PATTERN.matcher(url).find()) {
        // Compute list responses omit `items` entirely when the scope holds no resources, so
        // getItems() returns null rather than an empty list. Emitting `{}` keeps callers honest
        // about null-guarding; a fixture that returned `{"items":[]}` would hide that contract.
        return response.setStatusCode(200).setContent("{}");
      }
      return response.setStatusCode(404).setContent(NOT_FOUND_JSON);
    }

    String operationName = "operation-" + requests.size();
    String requestUrl = url.split("\\?", 2)[0];
    String resourceName =
        requestBody == null || requestBody.isEmpty()
            ? ""
            : objectMapper.readTree(requestBody).path("name").asText("");
    String targetLink =
        "POST".equals(method) && !resourceName.isEmpty()
            ? requestUrl + "/" + resourceName
            : requestUrl;
    return response
        .setStatusCode(200)
        .setContent(
            "{"
                + "\"name\":\""
                + operationName
                + "\","
                + "\"targetLink\":\""
                + targetLink
                + "\","
                + "\"status\":\"DONE\""
                + "}");
  }

  public List<CapturedRequest> getRequests() {
    return List.copyOf(requests);
  }

  public List<CapturedRequest> getWriteRequests() {
    return requests.stream().filter(request -> !"GET".equals(request.method())).toList();
  }

  public Optional<CapturedRequest> findRequest(String method, String pathSubstring) {
    return requests.stream()
        .filter(request -> request.method().equals(method))
        .filter(request -> request.url().contains(pathSubstring))
        .findFirst();
  }

  public Optional<CapturedRequest> findPostTo(String pathSubstring) {
    return findRequest("POST", pathSubstring);
  }

  public Optional<CapturedRequest> findPatchTo(String pathSubstring) {
    return findRequest("PATCH", pathSubstring);
  }

  public Optional<CapturedRequest> findPutTo(String pathSubstring) {
    return findRequest("PUT", pathSubstring);
  }

  public JsonNode parseBody(CapturedRequest request) throws IOException {
    if (request.body() == null || request.body().isEmpty()) {
      return objectMapper.createObjectNode();
    }
    return objectMapper.readTree(request.body());
  }

  public record CapturedRequest(String method, String url, String body) {}
}
