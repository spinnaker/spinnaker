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

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Captures outbound Compute HTTP requests (method, URL, JSON body) for API-boundary contract tests.
 * Public for reuse across handler and autoscaling operation test packages.
 */
public final class CapturingComputeTransport extends HttpTransport {
  private final List<CapturedRequest> requests = new ArrayList<>();
  private final String operationResponseJson;

  public CapturingComputeTransport() {
    this(
        "{"
            + "\"name\":\"operation-1\","
            + "\"targetLink\":\"https://www.googleapis.com/compute/v1/projects/test-project/regions/us-central1/instanceGroupManagers/example-server-group\","
            + "\"status\":\"DONE\""
            + "}");
  }

  public CapturingComputeTransport(String operationResponseJson) {
    this.operationResponseJson = operationResponseJson;
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
        requests.add(new CapturedRequest(method, url, getContentAsString()));
        return new MockLowLevelHttpResponse().setStatusCode(200).setContent(operationResponseJson);
      }
    };
  }

  public List<CapturedRequest> getRequests() {
    return List.copyOf(requests);
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

  public record CapturedRequest(String method, String url, String body) {}
}
