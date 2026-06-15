/*
 * Copyright 2025 Netflix, Inc.
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
import java.util.concurrent.TimeUnit;
import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.metrics.MetricRecord;

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
 */
public class SpectatorMetricPublisher implements MetricPublisher {

  private final Registry registry;

  public SpectatorMetricPublisher(Registry registry) {
    this.registry = requireNonNull(registry, "registry");
  }

  @Override
  public void publish(MetricCollection metricCollection) {
    String serviceName = extractStringMetric(metricCollection, "ServiceId", "unknown");
    String operationName = extractStringMetric(metricCollection, "OperationName", "unknown");

    // Record API call duration if available
    for (MetricRecord<?> record : metricCollection) {
      if ("ApiCallDuration".equals(record.metric().name()) && record.value() instanceof Duration) {
        Duration duration = (Duration) record.value();
        registry
            .timer(
                "aws.sdk.v2.apiCallDuration",
                "serviceName",
                serviceName,
                "operationName",
                operationName)
            .record(duration.toMillis(), TimeUnit.MILLISECONDS);
      }
      if ("RetryCount".equals(record.metric().name()) && record.value() instanceof Number) {
        int retries = ((Number) record.value()).intValue();
        if (retries > 0) {
          registry
              .counter(
                  "aws.sdk.v2.retryCount",
                  "serviceName",
                  serviceName,
                  "operationName",
                  operationName)
              .increment(retries);
        }
      }
    }

    // Recurse into child collections (e.g. per-attempt metrics)
    for (MetricCollection child : metricCollection.children()) {
      publish(child);
    }
  }

  @Override
  public void close() {
    // Nothing to clean up.
  }

  private String extractStringMetric(
      MetricCollection collection, String metricName, String defaultValue) {
    for (MetricRecord<?> record : collection) {
      if (metricName.equals(record.metric().name()) && record.value() instanceof String) {
        return (String) record.value();
      }
    }
    return defaultValue;
  }
}
