/*
 * Copyright 2020 Playtika.
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

package com.netflix.kayenta.prometheus.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@ExtendWith(MockitoExtension.class)
public class PrometheusHealthIndicatorTest {

  @Mock PrometheusHealthCache healthCache;
  @InjectMocks PrometheusHealthIndicator healthIndicator;

  @Test
  public void downWhenHealthStatusesEmpty() {
    when(healthCache.getHealthStatuses()).thenReturn(Collections.emptyList());

    Health health = healthIndicator.health();

    assertThat(health)
        .isEqualTo(Health.down().withDetail("reason", "Health status is not yet ready.").build());
  }

  @Test
  public void upWhenHealthStatusesAreAllUp() {
    when(healthCache.getHealthStatuses())
        .thenReturn(
            Arrays.asList(
                PrometheusHealthJob.PrometheusHealthStatus.builder()
                    .accountName("a1")
                    .status(Status.UP)
                    .build(),
                PrometheusHealthJob.PrometheusHealthStatus.builder()
                    .accountName("a2")
                    .status(Status.UP)
                    .build()));

    Health health = healthIndicator.health();

    assertThat(health)
        .isEqualTo(
            Health.up()
                .withDetail("a1", ImmutableMap.of("status", "UP"))
                .withDetail("a2", ImmutableMap.of("status", "UP"))
                .build());
  }

  @Test
  public void downWhenAtLeastOneHealthStatusIsDown() {
    when(healthCache.getHealthStatuses())
        .thenReturn(
            Arrays.asList(
                PrometheusHealthJob.PrometheusHealthStatus.builder()
                    .accountName("a1")
                    .status(Status.DOWN)
                    .errorDetails("some exception occurred")
                    .build(),
                PrometheusHealthJob.PrometheusHealthStatus.builder()
                    .accountName("a2")
                    .status(Status.UP)
                    .build()));

    Health health = healthIndicator.health();

    assertThat(health)
        .isEqualTo(
            Health.down()
                .withDetail("reason", "One of the Prometheus remote services is DOWN.")
                .withDetail(
                    "a1", ImmutableMap.of("status", "DOWN", "error", "some exception occurred"))
                .withDetail("a2", ImmutableMap.of("status", "UP"))
                .build());
  }
}
