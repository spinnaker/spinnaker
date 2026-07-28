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
 * Proves the fix: with the trailing slash {@link org.springframework.web.filter.UrlHandlerFilter}
 * registered (the default), a PUT to {@code /fetch/} is routed to the controller mapped to {@code
 * /fetch}.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {TrailingSlashTestConfig.class, FetchController.class})
class UrlHandlerFilterAutoConfigurationTest {

  @Autowired private TestRestTemplate restTemplate;

  private HttpEntity<String> request() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_PLAIN);
    return new HttpEntity<>("payload", headers);
  }

  @Test
  void trailingSlashIsRoutedToControllerWhenFilterEnabled() {
    ResponseEntity<String> response =
        restTemplate.exchange("/fetch/", HttpMethod.PUT, request(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("fetched:payload");
  }

  @Test
  void noTrailingSlashStillWorks() {
    ResponseEntity<String> response =
        restTemplate.exchange("/fetch", HttpMethod.PUT, request(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("fetched:payload");
  }

  @Test
  void trailingSlashIsNormalizedToNonSlashHandlerWhenEnabled() {
    // The filter trims the trailing slash before dispatch, so /fetch/ is served by the
    // @PutMapping("/fetch") handler. This proves normalization happens at the filter layer.
    ResponseEntity<String> response =
        restTemplate.exchange("/fetch/", HttpMethod.PUT, request(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("fetched:payload");
  }

  @Test
  void explicitTrailingSlashOnlyMappingBecomesUnreachableWhenEnabled() {
    // Caveat: the filter trims the trailing slash before dispatch, so a request to
    // /fetchWithSlash/ is dispatched as /fetchWithSlash. Because the controller only maps
    // /fetchWithSlash/ (with the slash) and nothing maps /fetchWithSlash, the request 404s.
    // In other words, endpoints that are intentionally mapped WITH a trailing slash become
    // unreachable while the filter is enabled.
    ResponseEntity<String> response =
        restTemplate.exchange("/fetchWithSlash/", HttpMethod.PUT, request(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void rootMappingIsNotBrokenWhenEnabled() {
    // Controllers like echo's HistoryController and gate's RootController map "/". Verify the
    // filter
    // does not trim the root path to an empty string and break it.
    ResponseEntity<String> response =
        restTemplate.exchange("/", HttpMethod.POST, request(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("root:payload");
  }
}
