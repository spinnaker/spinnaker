/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.kork.aws;

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.metrics.MetricCollector;
import com.amazonaws.metrics.RequestMetricCollector;
import com.amazonaws.metrics.ServiceMetricCollector;
import com.amazonaws.util.AWSRequestMetrics;
import com.amazonaws.util.AWSRequestMetrics.Field;
import com.amazonaws.util.TimingInfo;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

/**
 * Records AWS SDK v1 request metrics (client execute time, retries, throttling, exceptions) into a
 * Micrometer {@link MeterRegistry}. This replaces spectator-ext-aws's SpectatorMetricCollector now
 * that kork no longer carries Spectator as a core dependency; kork-aws itself is still on AWS SDK
 * v1 pending a separate migration to v2.
 */
public class MicrometerRequestMetricCollector extends MetricCollector {
  private final MeterRegistry registry;

  private final RequestMetricCollector requestMetricCollector =
      new RequestMetricCollector() {
        @Override
        public void collectMetrics(Request<?> request, Response<?> response) {
          MicrometerRequestMetricCollector.this.collectMetrics(request, response);
        }
      };

  public MicrometerRequestMetricCollector(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public boolean start() {
    return true;
  }

  @Override
  public boolean stop() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public RequestMetricCollector getRequestMetricCollector() {
    return requestMetricCollector;
  }

  @Override
  public ServiceMetricCollector getServiceMetricCollector() {
    return ServiceMetricCollector.NONE;
  }

  private void collectMetrics(Request<?> request, Response<?> response) {
    AWSRequestMetrics metrics = request.getAWSRequestMetrics();
    if (metrics == null) {
      return;
    }

    TimingInfo timingInfo = metrics.getTimingInfo();
    Tags tags =
        Tags.of(
            "service", request.getServiceName(),
            "request", request.getOriginalRequest().getClass().getSimpleName());

    Double clientExecuteTimeMs = timingInfo.getTimeTakenMillisIfKnown();
    if (clientExecuteTimeMs != null) {
      Timer.builder("aws.request")
          .tags(tags)
          .publishPercentileHistogram()
          .register(registry)
          .record(Duration.ofNanos(Math.round(clientExecuteTimeMs * 1_000_000)));
    }

    incrementCounter(timingInfo, Field.RequestCount, "aws.request.count", tags);
    incrementCounter(timingInfo, Field.HttpClientRetryCount, "aws.request.retries", tags);
    incrementCounter(timingInfo, Field.ThrottleException, "aws.request.throttling", tags);
    incrementCounter(timingInfo, Field.Exception, "aws.request.exceptions", tags);
  }

  private void incrementCounter(TimingInfo timingInfo, Field field, String name, Tags tags) {
    Number count = timingInfo.getCounter(field.name());
    if (count != null && count.doubleValue() > 0) {
      registry.counter(name, tags).increment(count.doubleValue());
    }
  }
}
