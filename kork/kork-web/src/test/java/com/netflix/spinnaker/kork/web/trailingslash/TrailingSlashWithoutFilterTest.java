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
 * Reproduces the bug: with the trailing slash filter disabled (Spring Boot 3.x default behavior), a
 * PUT to {@code /fetch/} returns 404 because it no longer matches the controller mapped to {@code
 * /fetch}. This is exactly the failure Orca hit calling {@code /artifacts/fetch/}.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {TrailingSlashTestConfig.class, FetchController.class},
    properties = "url-handler.trailing-slash.enabled=false")
class TrailingSlashWithoutFilterTest {

  @Autowired private TestRestTemplate restTemplate;

  private HttpEntity<String> request() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_PLAIN);
    return new HttpEntity<>("payload", headers);
  }

  @Test
  void trailingSlashReturns404WhenFilterDisabled() {
    ResponseEntity<String> response =
        restTemplate.exchange("/fetch/", HttpMethod.PUT, request(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void noTrailingSlashStillWorksWhenFilterDisabled() {
    ResponseEntity<String> response =
        restTemplate.exchange("/fetch", HttpMethod.PUT, request(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("fetched:payload");
  }
}
