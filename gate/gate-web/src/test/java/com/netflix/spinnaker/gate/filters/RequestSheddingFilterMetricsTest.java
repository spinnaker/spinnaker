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

package com.netflix.spinnaker.gate.filters;

import static com.netflix.spinnaker.gate.filters.RequestLoggingFilter.REQUEST_START_TIME;
import static com.netflix.spinnaker.gate.filters.RequestSheddingFilter.CHANCE_KEY;
import static com.netflix.spinnaker.gate.filters.RequestSheddingFilter.ENABLED_KEY;
import static com.netflix.spinnaker.gate.filters.RequestSheddingFilter.PATHS_KEY;
import static com.netflix.spinnaker.gate.filters.RequestSheddingFilter.PRIORITY_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Pins the metrics {@link RequestSheddingFilter} publishes when it drops a request. */
class RequestSheddingFilterMetricsTest {

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final DynamicConfigService configService = mock(DynamicConfigService.class);
  private RequestSheddingFilter filter;

  @BeforeEach
  void setUp() {
    when(configService.isEnabled(ENABLED_KEY, false)).thenReturn(true);
    when(configService.getConfig(String.class, PATHS_KEY, "")).thenReturn("/applications");
    when(configService.getConfig(Integer.class, CHANCE_KEY, 100)).thenReturn(100);
    filter = new RequestSheddingFilter(configService, meters, null);
    filter.compilePatterns();
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void droppedRequestRecordsControllerInvocationAndDropCounter() throws Exception {
    MDC.put(REQUEST_START_TIME, String.valueOf(System.currentTimeMillis()));

    filter.doFilter(lowPriorityRequest(), new MockHttpServletResponse(), mock(FilterChain.class));

    Timer timer = meters.find("controller.invocations").timer();
    assertThat(meters.find("controller.invocations").timers()).hasSize(1);
    assertThat(meters.find("controller.invocations").counters()).isEmpty();
    assertThat(Tags.of(timer.getId().getTags()))
        .isEqualTo(
            Tags.of(
                "controller", "unknown",
                "method", "unknown",
                "status", "5xx",
                "statusCode", "503",
                "success", "false",
                "cause", "RequestSheddingFilter"));
    assertThat(timer.count()).isEqualTo(1);
    assertThat(meters.find("requestShedding.requests").tags("action", "dropped").counter().count())
        .isEqualTo(1);
  }

  @Test
  void droppedRequestWithoutStartTimeRecordsOnlyTheDropCounter() throws Exception {
    filter.doFilter(lowPriorityRequest(), new MockHttpServletResponse(), mock(FilterChain.class));

    assertThat(meters.find("controller.invocations").timers()).isEmpty();
    assertThat(meters.find("requestShedding.requests").tags("action", "dropped").counter().count())
        .isEqualTo(1);
  }

  private static MockHttpServletRequest lowPriorityRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/applications");
    request.addHeader(PRIORITY_HEADER, "low");
    return request;
  }
}
