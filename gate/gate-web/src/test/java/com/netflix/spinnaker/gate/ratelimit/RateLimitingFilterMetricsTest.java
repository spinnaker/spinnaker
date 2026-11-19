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

package com.netflix.spinnaker.gate.ratelimit;

import static com.netflix.spinnaker.gate.filters.RequestLoggingFilter.REQUEST_START_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.config.RateLimiterConfiguration;
import com.netflix.spinnaker.gate.security.RequestIdentityExtractor;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/** Pins the metrics {@link RateLimitingFilter} publishes when it throttles a request. */
class RateLimitingFilterMetricsTest {

  private static final String PRINCIPAL = "test-user@example.com";

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final RateLimiter rateLimiter = mock(RateLimiter.class);
  private final RequestIdentityExtractor identityExtractor = mock(RequestIdentityExtractor.class);
  private RateLimitingFilter filter;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    when(identityExtractor.supports(any())).thenReturn(true);
    when(identityExtractor.extractIdentity(any())).thenReturn(PRINCIPAL);
    when(rateLimiter.incrementAndGetRate(any())).thenReturn(throttledRate());

    RateLimiterConfiguration config = new RateLimiterConfiguration();
    filter =
        new RateLimitingFilter(
            rateLimiter,
            meters,
            (name, sourceApp) -> new RateLimitPrincipal(name, 10, 1, false),
            new DefaultRateLimitPrincipalResolver(List.of(identityExtractor)),
            new RateLimitRuleMatcher(config.getRules(), config.getExcludePathPatterns()));
  }

  @AfterEach
  void clear() {
    MDC.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void throttledRequestRecordsControllerInvocationAndThrottlingCounters() throws Exception {
    MDC.put(REQUEST_START_TIME, String.valueOf(System.currentTimeMillis()));

    filter.doFilter(
        new MockHttpServletRequest("GET", "/applications"),
        new MockHttpServletResponse(),
        mock(FilterChain.class));

    Timer timer = meters.find("controller.invocations").timer();
    assertThat(meters.find("controller.invocations").timers()).hasSize(1);
    assertThat(meters.find("controller.invocations").counters()).isEmpty();
    assertThat(Tags.of(timer.getId().getTags()))
        .isEqualTo(
            Tags.of(
                "controller", "unknown",
                "method", "unknown",
                "status", "4xx",
                "statusCode", "429",
                "success", "false",
                "cause", "RateLimitingFilter"));
    assertThat(timer.count()).isEqualTo(1);
    assertThat(meters.find("rateLimit.throttling").counter().count()).isEqualTo(1);
    assertThat(meters.find("rateLimit.throttlingLearning").counter().count()).isZero();
    assertThat(
            meters
                .find("rateLimit.principal.throttled")
                .tags("principal", PRINCIPAL)
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            meters
                .find("rateLimit.principal.requests")
                .tags("principal", PRINCIPAL)
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void throttledRequestWithoutStartTimeRecordsNoControllerInvocation() throws Exception {
    filter.doFilter(
        new MockHttpServletRequest("GET", "/applications"),
        new MockHttpServletResponse(),
        mock(FilterChain.class));

    assertThat(meters.find("controller.invocations").timers()).isEmpty();
    assertThat(meters.find("rateLimit.throttling").counter().count()).isEqualTo(1);
  }

  private static Rate throttledRate() {
    Rate rate = new Rate();
    rate.capacity = 1;
    rate.rateSeconds = 10;
    rate.remaining = -1;
    rate.reset = 0L;
    rate.throttled = true;
    return rate;
  }
}
