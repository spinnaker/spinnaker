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

package com.netflix.spinnaker.gate.filters;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import java.io.IOException;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestMetricsFilterTest {

  private Registry registry;
  private RequestMetricsFilter filter;

  @BeforeEach
  void setUp() {
    registry = new DefaultRegistry();
    filter = new RequestMetricsFilter(registry);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ---------------------------------------------------------------------------
  // Happy path — API-token request
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("API token request → counter tagged api_token / service_account / status code")
  void apiTokenRequest() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/applications");
    request.setAttribute(AuthTypeResolver.AUTH_TYPE_ATTRIBUTE, AuthTypeResolver.TYPE_API_TOKEN);
    request.setAttribute(
        AuthTypeResolver.PRINCIPAL_KIND_ATTRIBUTE, AuthTypeResolver.PRINCIPAL_KIND_SERVICE_ACCOUNT);
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(201);

    filter.doFilterInternal(request, response, new MockFilterChain());

    Counter c =
        findSingleCounter(
            registry,
            RequestMetricsFilter.METRIC_NAME,
            "authType",
            AuthTypeResolver.TYPE_API_TOKEN);
    assertThat(c.count()).isEqualTo(1L);
    assertThat(tag(c, "principalKind")).isEqualTo("service_account");
    assertThat(tag(c, "method")).isEqualTo("POST");
    assertThat(tag(c, "statusCode")).isEqualTo("201");
    assertThat(tag(c, "status")).isEqualTo("2xx");
  }

  // ---------------------------------------------------------------------------
  // Heuristic — session request via SecurityContextHolder
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("authenticated UsernamePassword session → authType=session, principalKind=unknown")
  void sessionRequest() throws ServletException, IOException {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "alice", "pw", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/credentials");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);

    filter.doFilterInternal(request, response, new MockFilterChain());

    Counter c =
        findSingleCounter(
            registry, RequestMetricsFilter.METRIC_NAME, "authType", AuthTypeResolver.TYPE_SESSION);
    assertThat(tag(c, "principalKind")).isEqualTo(AuthTypeResolver.PRINCIPAL_KIND_UNKNOWN);
    assertThat(tag(c, "method")).isEqualTo("GET");
    assertThat(tag(c, "statusCode")).isEqualTo("200");
    assertThat(tag(c, "status")).isEqualTo("2xx");
  }

  // ---------------------------------------------------------------------------
  // Unauthenticated traffic
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("unauthenticated request returning 401 → authType=none, status=4xx")
  void unauthenticatedRequest() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secured");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(401);

    filter.doFilterInternal(request, response, chain);

    Counter c =
        findSingleCounter(
            registry, RequestMetricsFilter.METRIC_NAME, "authType", AuthTypeResolver.TYPE_NONE);
    assertThat(tag(c, "statusCode")).isEqualTo("401");
    assertThat(tag(c, "status")).isEqualTo("4xx");
  }

  // ---------------------------------------------------------------------------
  // Exception inside the chain still records the counter (via try/finally)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("downstream exception still increments the counter (try/finally)")
  void downstreamExceptionStillRecords() {
    MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/applications/foo");
    request.setAttribute(AuthTypeResolver.AUTH_TYPE_ATTRIBUTE, AuthTypeResolver.TYPE_API_TOKEN);
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(500);
    FilterChain chain =
        (req, res) -> {
          throw new ServletException("boom");
        };

    try {
      filter.doFilterInternal(request, response, chain);
    } catch (Exception expected) {
      // swallow — we only care that the counter still incremented
    }

    Counter c =
        findSingleCounter(
            registry,
            RequestMetricsFilter.METRIC_NAME,
            "authType",
            AuthTypeResolver.TYPE_API_TOKEN);
    assertThat(c.count()).isEqualTo(1L);
    assertThat(tag(c, "method")).isEqualTo("DELETE");
    assertThat(tag(c, "statusCode")).isEqualTo("500");
    assertThat(tag(c, "status")).isEqualTo("5xx");
  }

  // ---------------------------------------------------------------------------
  // Distinct tag combinations yield distinct counters (not aggregated together)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("two requests with different auth types → two distinct counter series")
  void distinctTagSetsAreNotMerged() throws ServletException, IOException {
    MockHttpServletRequest apiReq = new MockHttpServletRequest("GET", "/a");
    apiReq.setAttribute(AuthTypeResolver.AUTH_TYPE_ATTRIBUTE, AuthTypeResolver.TYPE_API_TOKEN);
    MockHttpServletResponse apiRes = new MockHttpServletResponse();
    apiRes.setStatus(200);
    filter.doFilterInternal(apiReq, apiRes, new MockFilterChain());

    MockHttpServletRequest anonReq = new MockHttpServletRequest("GET", "/b");
    MockHttpServletResponse anonRes = new MockHttpServletResponse();
    anonRes.setStatus(200);
    filter.doFilterInternal(anonReq, anonRes, new MockFilterChain());

    Counter api =
        findSingleCounter(
            registry,
            RequestMetricsFilter.METRIC_NAME,
            "authType",
            AuthTypeResolver.TYPE_API_TOKEN);
    Counter none =
        findSingleCounter(
            registry, RequestMetricsFilter.METRIC_NAME, "authType", AuthTypeResolver.TYPE_NONE);
    assertThat(api.count()).isEqualTo(1L);
    assertThat(none.count()).isEqualTo(1L);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Returns the single counter under {@code metricName} carrying {@code tagKey=tagValue}. Fails the
   * test if zero or more than one counter matches.
   */
  private static Counter findSingleCounter(
      Registry registry, String metricName, String tagKey, String tagValue) {
    List<Counter> matching =
        registry
            .counters()
            .filter(c -> c.id().name().equals(metricName))
            .filter(c -> tagValue.equals(tag(c, tagKey)))
            .toList();
    assertThat(matching)
        .as("expected exactly one counter %s with %s=%s", metricName, tagKey, tagValue)
        .hasSize(1);
    return matching.get(0);
  }

  private static String tag(Counter counter, String key) {
    for (com.netflix.spectator.api.Tag t : counter.id().tags()) {
      if (t.key().equals(key)) {
        return t.value();
      }
    }
    return null;
  }
}
