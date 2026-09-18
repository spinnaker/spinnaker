/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.metrics.CoreMetric;
import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricCollector;

/** Unit tests for {@link MicrometerMetricPublisher}. */
class MicrometerMetricPublisherTest {

  private MeterRegistry registry;
  private MicrometerMetricPublisher publisher;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    publisher = new MicrometerMetricPublisher(registry);
  }

  @Test
  void publish_recordsApiCallDuration() {
    MetricCollector collector = MetricCollector.create("test");
    collector.reportMetric(CoreMetric.SERVICE_ID, "Ecs");
    collector.reportMetric(CoreMetric.OPERATION_NAME, "ListServices");
    collector.reportMetric(CoreMetric.API_CALL_DURATION, Duration.ofMillis(250));
    MetricCollection collection = collector.collect();

    publisher.publish(collection);

    long totalTime =
        (long)
            registry
                .timer(
                    "aws.sdk.v2.apiCallDuration",
                    "serviceName",
                    "Ecs",
                    "operationName",
                    "ListServices")
                .totalTime(TimeUnit.NANOSECONDS);
    // Timer records in nanoseconds; 250ms = 250_000_000 ns
    assertThat(totalTime).isEqualTo(250_000_000L);
  }

  @Test
  void publish_recordsRetryCount() {
    MetricCollector collector = MetricCollector.create("test");
    collector.reportMetric(CoreMetric.SERVICE_ID, "Ecs");
    collector.reportMetric(CoreMetric.OPERATION_NAME, "DescribeServices");
    collector.reportMetric(CoreMetric.RETRY_COUNT, 3);
    MetricCollection collection = collector.collect();

    publisher.publish(collection);

    long count =
        (long)
            registry
                .counter(
                    "aws.sdk.v2.retryCount",
                    "serviceName",
                    "Ecs",
                    "operationName",
                    "DescribeServices")
                .count();
    assertThat(count).isEqualTo(3L);
  }

  @Test
  void publish_zeroRetries_doesNotIncrementCounter() {
    MetricCollector collector = MetricCollector.create("test");
    collector.reportMetric(CoreMetric.SERVICE_ID, "Ecs");
    collector.reportMetric(CoreMetric.OPERATION_NAME, "ListTasks");
    collector.reportMetric(CoreMetric.RETRY_COUNT, 0);
    MetricCollection collection = collector.collect();

    publisher.publish(collection);

    long count =
        (long)
            registry
                .counter(
                    "aws.sdk.v2.retryCount", "serviceName", "Ecs", "operationName", "ListTasks")
                .count();
    assertThat(count).isEqualTo(0L);
  }

  @Test
  void publish_missingServiceId_usesUnknown() {
    MetricCollector collector = MetricCollector.create("test");
    collector.reportMetric(CoreMetric.API_CALL_DURATION, Duration.ofMillis(100));
    MetricCollection collection = collector.collect();

    publisher.publish(collection);

    long totalTime =
        (long)
            registry
                .timer(
                    "aws.sdk.v2.apiCallDuration",
                    "serviceName",
                    "unknown",
                    "operationName",
                    "unknown")
                .totalTime(TimeUnit.NANOSECONDS);
    assertThat(totalTime).isEqualTo(100_000_000L);
  }

  @Test
  void publish_recursesIntoChildren() {
    MetricCollector parent = MetricCollector.create("parent");
    parent.reportMetric(CoreMetric.SERVICE_ID, "Ecr");
    parent.reportMetric(CoreMetric.OPERATION_NAME, "GetAuthorizationToken");

    MetricCollector child = parent.createChild("attempt");
    child.reportMetric(CoreMetric.SERVICE_ID, "Ecr");
    child.reportMetric(CoreMetric.OPERATION_NAME, "GetAuthorizationToken");
    child.reportMetric(CoreMetric.API_CALL_DURATION, Duration.ofMillis(50));

    MetricCollection collection = parent.collect();

    publisher.publish(collection);

    long totalTime =
        (long)
            registry
                .timer(
                    "aws.sdk.v2.apiCallDuration",
                    "serviceName",
                    "Ecr",
                    "operationName",
                    "GetAuthorizationToken")
                .totalTime(TimeUnit.NANOSECONDS);
    assertThat(totalTime).isEqualTo(50_000_000L);
  }
}
