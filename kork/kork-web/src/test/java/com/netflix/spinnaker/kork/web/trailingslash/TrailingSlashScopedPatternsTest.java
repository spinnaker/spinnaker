/*
 * Copyright 2026 Harness, Inc.
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
package com.netflix.spinnaker.kork.web.trailingslash;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Shows how to cover endpoints that are intentionally mapped WITH a trailing slash. By scoping
 * {@code url-handler.trailing-slash.path-patterns} so it does not match those routes, the filter
 * leaves them untouched (so they keep working), while still normalizing the routes that are listed.
 *
 * <p>Here the filter is scoped to {@code /fetch/} only, so:
 *
 * <ul>
 *   <li>{@code /fetch/} is trimmed and served by the {@code /fetch} handler.
 *   <li>{@code /fetchWithSlash/} is NOT trimmed, so its explicit trailing-slash mapping is
 *       reachable.
 * </ul>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {TrailingSlashTestConfig.class, FetchController.class},
    properties = "url-handler.trailing-slash.path-patterns=/fetch/")
class TrailingSlashScopedPatternsTest {

  @Autowired private TestRestTemplate restTemplate;

  private HttpEntity<String> request() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_PLAIN);
    return new HttpEntity<>("payload", headers);
  }

  @Test
  void scopedRouteIsStillNormalized() {
    ResponseEntity<String> response =
        restTemplate.exchange("/fetch/", HttpMethod.PUT, request(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("fetched:payload");
  }

  @Test
  void explicitTrailingSlashRouteOutsideScopeRemainsReachable() {
    ResponseEntity<String> response =
        restTemplate.exchange("/fetchWithSlash/", HttpMethod.PUT, request(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("fetchedWithSlash:payload");
  }
}
