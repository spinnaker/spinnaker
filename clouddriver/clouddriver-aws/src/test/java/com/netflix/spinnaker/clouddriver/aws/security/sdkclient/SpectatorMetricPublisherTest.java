/*
 * Copyright 2025 Netflix, Inc.
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

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.metrics.CoreMetric;
import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricCollector;

/** Unit tests for {@link SpectatorMetricPublisher}. */
class SpectatorMetricPublisherTest {

  private Registry registry;
  private SpectatorMetricPublisher publisher;

  @BeforeEach
  void setUp() {
    registry = new DefaultRegistry();
    publisher = new SpectatorMetricPublisher(registry);
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
        registry
            .timer(
                registry
                    .createId("aws.sdk.v2.apiCallDuration")
                    .withTag("serviceName", "Ecs")
                    .withTag("operationName", "ListServices"))
            .totalTime();
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
        registry
            .counter(
                registry
                    .createId("aws.sdk.v2.retryCount")
                    .withTag("serviceName", "Ecs")
                    .withTag("operationName", "DescribeServices"))
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
        registry
            .counter(
                registry
                    .createId("aws.sdk.v2.retryCount")
                    .withTag("serviceName", "Ecs")
                    .withTag("operationName", "ListTasks"))
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
        registry
            .timer(
                registry
                    .createId("aws.sdk.v2.apiCallDuration")
                    .withTag("serviceName", "unknown")
                    .withTag("operationName", "unknown"))
            .totalTime();
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
        registry
            .timer(
                registry
                    .createId("aws.sdk.v2.apiCallDuration")
                    .withTag("serviceName", "Ecr")
                    .withTag("operationName", "GetAuthorizationToken"))
            .totalTime();
    assertThat(totalTime).isEqualTo(50_000_000L);
  }
}
