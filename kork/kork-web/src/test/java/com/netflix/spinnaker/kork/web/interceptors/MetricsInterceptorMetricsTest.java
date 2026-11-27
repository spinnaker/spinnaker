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

package com.netflix.spinnaker.kork.web.interceptors;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spectator.micrometer.MicrometerRegistry;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Pins the metrics {@link MetricsInterceptor} publishes: names, exact tags, counts and amounts.
 * Assertions are made on the Micrometer registry the Spectator bridge writes to.
 */
class MetricsInterceptorMetricsTest {

  private static final String METRIC = "controller.invocations";
  private static final long START_NANOS = 1_000L;
  private static final long END_NANOS = 3_500L;

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final MetricsInterceptor interceptor =
      new MetricsInterceptor(
          new MicrometerRegistry(meters),
          METRIC,
          List.of("account"),
          List.of("app"),
          List.of("ExcludedController")) {
        private boolean started;

        @Override
        protected Long getNanoTime() {
          long now = started ? END_NANOS : START_NANOS;
          started = true;
          return now;
        }
      };

  @Test
  void successfulRequestRecordsTimerAndContentLengthWithAllTags() throws Exception {
    MockHttpServletRequest request = request(Map.of("account", "test-account"));
    request.setParameter("app", "test-app");
    request.setContent(new byte[] {1, 2, 3});

    call(request, 200, handler(new TestController(), "get"), null);

    Tags expected =
        Tags.of(
            "controller", "TestController",
            "method", "get",
            "status", "2xx",
            "statusCode", "200",
            "criticality", "unknown",
            "account", "test-account",
            "app", "test-app",
            "success", "true",
            "cause", "None");
    Timer timer = onlyTimer();
    assertThat(Tags.of(timer.getId().getTags())).isEqualTo(expected);
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(END_NANOS - START_NANOS);

    DistributionSummary contentLength = onlyContentLengthSummary();
    assertThat(Tags.of(contentLength.getId().getTags())).isEqualTo(expected);
    assertThat(contentLength.count()).isEqualTo(1);
    assertThat(contentLength.totalAmount()).isEqualTo(3);
  }

  @Test
  void missingPathVariableAndQueryParameterAreTaggedNone() throws Exception {
    call(request(Map.of()), 200, handler(new TestController(), "get"), null);

    assertThat(onlyTimer().getId().getTag("account")).isEqualTo("None");
    assertThat(onlyTimer().getId().getTag("app")).isEqualTo("None");
  }

  @Test
  void clientErrorWithoutExceptionKeepsResponseStatus() throws Exception {
    call(request(Map.of()), 404, handler(new TestController(), "get"), null);

    Timer timer = onlyTimer();
    assertThat(timer.getId().getTag("status")).isEqualTo("4xx");
    assertThat(timer.getId().getTag("statusCode")).isEqualTo("404");
    assertThat(timer.getId().getTag("success")).isEqualTo("true");
    assertThat(timer.getId().getTag("cause")).isEqualTo("None");
  }

  @Test
  void exceptionIsRecordedAsServerErrorWithItsSimpleName() throws Exception {
    call(
        request(Map.of()),
        200,
        handler(new TestController(), "get"),
        new IllegalStateException("boom"));

    Timer timer = onlyTimer();
    assertThat(timer.getId().getTag("status")).isEqualTo("5xx");
    assertThat(timer.getId().getTag("statusCode")).isEqualTo("500");
    assertThat(timer.getId().getTag("success")).isEqualTo("false");
    assertThat(timer.getId().getTag("cause")).isEqualTo("IllegalStateException");
    assertThat(onlyContentLengthSummary().getId().getTag("cause"))
        .isEqualTo("IllegalStateException");
  }

  @Test
  void methodCriticalityWinsOverClassCriticality() throws Exception {
    call(request(Map.of()), 200, handler(new AnnotatedController(), "high"), null);

    assertThat(onlyTimer().getId().getTag("criticality")).isEqualTo(Criticality.Value.HIGH);
  }

  @Test
  void classCriticalityAppliesWhenMethodHasNone() throws Exception {
    call(request(Map.of()), 200, handler(new AnnotatedController(), "get"), null);

    assertThat(onlyTimer().getId().getTag("criticality")).isEqualTo(Criticality.Value.LOW);
  }

  @Test
  void requestWithoutBodyRegistersContentLengthWithoutRecording() throws Exception {
    call(request(Map.of()), 200, handler(new TestController(), "get"), null);

    assertThat(onlyContentLengthSummary().count()).isZero();
  }

  @Test
  void excludedControllerRecordsNothing() throws Exception {
    call(request(Map.of()), 200, handler(new ExcludedController(), "get"), null);

    assertThat(meters.getMeters()).isEmpty();
  }

  @Test
  void nonHandlerMethodRecordsNothing() throws Exception {
    call(request(Map.of()), 200, new Object(), null);

    assertThat(meters.getMeters()).isEmpty();
  }

  private Timer onlyTimer() {
    assertThat(meters.find(METRIC).timers()).hasSize(1);
    return meters.find(METRIC).timer();
  }

  private DistributionSummary onlyContentLengthSummary() {
    String name = METRIC + ".contentLength";
    assertThat(meters.find(name).summaries()).hasSize(1);
    return meters.find(name).summary();
  }

  private void call(MockHttpServletRequest request, int status, Object handler, Exception exception)
      throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(status);
    interceptor.preHandle(request, response, handler);
    interceptor.afterCompletion(request, response, handler, exception);
  }

  private static MockHttpServletRequest request(Map<String, String> pathVariables) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);
    return request;
  }

  private static HandlerMethod handler(Object bean, String method) throws NoSuchMethodException {
    return new HandlerMethod(bean, method);
  }

  private static class TestController {
    public void get() {}
  }

  @Criticality(Criticality.Value.LOW)
  private static class AnnotatedController {
    public void get() {}

    @Criticality(Criticality.Value.HIGH)
    public void high() {}
  }

  private static class ExcludedController {
    public void get() {}
  }
}
