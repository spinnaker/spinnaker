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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

public class PrometheusHealthIndicator extends AbstractHealthIndicator {

  private final PrometheusHealthCache healthCache;

  public PrometheusHealthIndicator(PrometheusHealthCache healthCache) {
    this.healthCache = healthCache;
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    List<PrometheusHealthJob.PrometheusHealthStatus> healthStatuses =
        healthCache.getHealthStatuses();

    if (healthStatuses.isEmpty()) {
      builder.status(Status.DOWN).withDetail("reason", "Health status is not yet ready.");
      return;
    }

    boolean anyIsDown =
        healthStatuses.stream().anyMatch(status -> status.getStatus().equals(Status.DOWN));

    if (anyIsDown) {
      builder
          .status(Status.DOWN)
          .withDetail("reason", "One of the Prometheus remote services is DOWN.");
    } else {
      builder.status(Status.UP);
    }

    healthStatuses.forEach(
        healthStatus -> {
          Map<String, Object> details = new HashMap<>();
          details.put("status", healthStatus.getStatus().getCode());
          if (healthStatus.getErrorDetails() != null) {
            details.put("error", healthStatus.getErrorDetails());
          }
          builder.withDetail(healthStatus.getAccountName(), details);
        });
  }
}
