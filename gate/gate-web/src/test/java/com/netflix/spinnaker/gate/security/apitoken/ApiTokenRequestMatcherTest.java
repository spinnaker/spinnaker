/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.gate.security.apitoken;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Unit tests for {@link ApiTokenAuthConfigurerAdapter.ApiTokenRequestMatcher}, the {@link
 * RequestMatcher} that routes Spinnaker API-token requests to the dedicated stateless security
 * chain.
 */
class ApiTokenRequestMatcherTest {

  private static final String TOKEN_PREFIX = "spk_";
  private static final String VALID_TOKEN = TOKEN_PREFIX + "testtoken";

  private final RequestMatcher matcher =
      new ApiTokenAuthConfigurerAdapter.ApiTokenRequestMatcher(TOKEN_PREFIX);

  @Nested
  @DisplayName("matches() returns true")
  class Matches {

    @Test
    @DisplayName("for a standard 'Bearer ' scheme")
    void standardBearer() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer " + VALID_TOKEN);

      assertThat(matcher.matches(request)).isTrue();
    }

    @Test
    @DisplayName("for a lowercase 'bearer ' scheme")
    void lowercaseBearer() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "bearer " + VALID_TOKEN);

      assertThat(matcher.matches(request)).isTrue();
    }

    @Test
    @DisplayName("for an uppercase 'BEARER ' scheme")
    void uppercaseBearer() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "BEARER " + VALID_TOKEN);

      assertThat(matcher.matches(request)).isTrue();
    }
  }

  @Nested
  @DisplayName("matches() returns false")
  class DoesNotMatch {

    @Test
    @DisplayName("for a non-Bearer scheme (Basic auth)")
    void wrongScheme() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

      assertThat(matcher.matches(request)).isFalse();
    }

    @Test
    @DisplayName("when no Authorization header is present")
    void noAuthorizationHeader() {
      MockHttpServletRequest request = new MockHttpServletRequest();

      assertThat(matcher.matches(request)).isFalse();
    }

    @Test
    @DisplayName("when the Bearer token is missing the configured token prefix")
    void missingTokenPrefix() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer no-prefix-token");

      assertThat(matcher.matches(request)).isFalse();
    }
  }

  @Nested
  @DisplayName("matches() via X-Spinnaker-Token header")
  class XSpinnakerTokenHeader {

    @Test
    @DisplayName("matches when X-Spinnaker-Token header is present")
    void headerPresent() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("X-Spinnaker-Token", VALID_TOKEN);
      assertThat(matcher.matches(request)).isTrue();
    }

    @Test
    @DisplayName("does not match when X-Spinnaker-Token is absent and no Authorization header")
    void headerAbsentNoAuth() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      assertThat(matcher.matches(request)).isFalse();
    }

    @Test
    @DisplayName("falls through to Bearer check when X-Spinnaker-Token is absent")
    void headerAbsentFallsThroughToBearer() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
      assertThat(matcher.matches(request)).isTrue();
    }
  }
}
