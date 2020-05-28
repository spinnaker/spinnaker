/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.front50;

import static java.lang.String.format;

import com.netflix.spinnaker.front50.model.ItemDAO;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.TaskScheduler;

public class ItemDAOHealthIndicator implements HealthIndicator, Runnable {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ItemDAO itemDAO;
  private final AtomicReference<Health> lastHealth = new AtomicReference<Health>(null);

  public ItemDAOHealthIndicator(ItemDAO<?> itemDAO, TaskScheduler taskScheduler) {
    this.itemDAO = itemDAO;
    taskScheduler.scheduleWithFixedDelay(this, itemDAO.getHealthIntervalMillis());
  }

  @Override
  public Health health() {
    if (lastHealth.get() == null) {
      return new Health.Builder().down().build();
    }
    return lastHealth.get();
  }

  public void run() {
    Health.Builder healthBuilder = new Health.Builder().up();

    try {
      if (itemDAO.isHealthy()) {
        healthBuilder.withDetail(itemDAO.getClass().getSimpleName(), "Healthy");
      } else {
        healthBuilder.down().withDetail(itemDAO.getClass().getSimpleName(), "Unhealthy");
      }

    } catch (RuntimeException e) {
      log.error("ItemDAO {} health check failed", itemDAO.getClass().getSimpleName(), e);
      healthBuilder
          .down()
          .withDetail(
              itemDAO.getClass().getSimpleName(), format("Unhealthy: `%s`", e.getMessage()));
    }

    lastHealth.set(healthBuilder.build());
  }
}
