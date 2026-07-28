/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import static java.util.Objects.requireNonNull;

import com.netflix.spectator.api.Registry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import software.amazon.awssdk.core.metrics.CoreMetric;
import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricPublisher;

/**
 * A {@link MetricPublisher} that bridges AWS SDK v2 metric events to the Spectator {@link
 * Registry}.
 *
 * <p>Records:
 *
 * <ul>
 *   <li>{@code aws.sdk.v2.apiCallDuration} — timer of overall API call duration
 *   <li>{@code aws.sdk.v2.retryCount} — counter of retry attempts
 * </ul>
 *
 * <p>Metrics are tagged with {@code serviceName} and {@code operationName} when available.
 *
 * <p>TODO: Consider migrating to Micrometer's MeterRegistry when Spectator is phased out in favour
 * of OTEL-aligned instrumentation.
 */
public class SpectatorMetricPublisher implements MetricPublisher {

  private static final int MAX_DEPTH = 5;

  private final Registry registry;

  public SpectatorMetricPublisher(Registry registry) {
    this.registry = requireNonNull(registry, "registry");
  }

  @Override
  public void publish(MetricCollection metricCollection) {
    publish(metricCollection, 0);
  }

  private void publish(MetricCollection metricCollection, int depth) {
    if (depth > MAX_DEPTH) {
      return;
    }

    List<String> serviceIds = metricCollection.metricValues(CoreMetric.SERVICE_ID);
    String serviceName = serviceIds.isEmpty() ? "unknown" : serviceIds.get(0);

    List<String> operationNames = metricCollection.metricValues(CoreMetric.OPERATION_NAME);
    String operationName = operationNames.isEmpty() ? "unknown" : operationNames.get(0);

    // Record API call duration if available
    List<Duration> durations = metricCollection.metricValues(CoreMetric.API_CALL_DURATION);
    for (Duration duration : durations) {
      registry
          .timer(
              "aws.sdk.v2.apiCallDuration",
              "serviceName",
              serviceName,
              "operationName",
              operationName)
          .record(duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    // Record retry count if > 0
    List<Integer> retryCounts = metricCollection.metricValues(CoreMetric.RETRY_COUNT);
    for (Integer retries : retryCounts) {
      if (retries > 0) {
        registry
            .counter(
                "aws.sdk.v2.retryCount", "serviceName", serviceName, "operationName", operationName)
            .increment(retries);
      }
    }

    for (MetricCollection child : metricCollection.children()) {
      publish(child, depth + 1);
    }
  }

  @Override
  public void close() {
    // Nothing to clean up.
  }
}
