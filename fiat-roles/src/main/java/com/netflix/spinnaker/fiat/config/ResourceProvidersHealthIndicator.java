/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.config;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.providers.HealthTrackable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnExpression("${fiat.write-mode.enabled:true}")
public class ResourceProvidersHealthIndicator extends AbstractHealthIndicator {

  @Autowired @Setter List<HealthTrackable> providers;

  @Autowired @Setter Registry registry;

  private AtomicBoolean previousHealthCheckIsUp = new AtomicBoolean(false);

  private AtomicBoolean upOnce = new AtomicBoolean(false);

  private Id id(String name, HealthTrackable provider) {
    return registry
        .createId("fiat.resourceProvider." + name)
        .withTag("provider", provider.getClass().getSimpleName());
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) throws Exception {
    boolean isDown = false;
    for (HealthTrackable provider : providers) {
      builder.withDetail(
          provider.getClass().getSimpleName(), provider.getHealthTracker().getHealthView());
      isDown = isDown || !provider.getHealthTracker().isProviderHealthy();
      registry
          .gauge(id("healthy", provider))
          .set(provider.getHealthTracker().isProviderHealthy() ? 1 : 0);
      long dataAge = provider.getHealthTracker().getHealthView().getMsSinceLastSuccess();
      long timeTillStaleMillis =
          provider.getHealthTracker().getHealthView().getMaximumStalenessTimeMs() - dataAge;
      registry
          .gauge(id("dataAgeSeconds", provider).withTag("stale", timeTillStaleMillis < 0))
          .set(((double) dataAge) / 1000.0d);
    }

    if (isDown) {
      if (previousHealthCheckIsUp.getAndSet(false)) {
        log.warn("Server is now UNHEALTHY");
      }
      if (upOnce.get()) {
        builder.up();
      } else {
        builder.down();
      }
    } else {
      if (!previousHealthCheckIsUp.getAndSet(true)) {
        log.info("Server is now HEALTHY. Hooray!");
      }
      builder.up();
      upOnce.set(true);
    }
  }
}
