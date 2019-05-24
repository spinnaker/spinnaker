/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.front50.config

import com.netflix.spinnaker.front50.model.ItemDAO
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.scheduling.TaskScheduler

import java.util.concurrent.atomic.AtomicReference

class ItemDAOHealthIndicator implements HealthIndicator, Runnable {

  private final Logger log = LoggerFactory.getLogger(getClass())
  private final ItemDAO itemDAO
  private final AtomicReference<Health> lastHealth = new AtomicReference<>(null)

  ItemDAOHealthIndicator(ItemDAO itemDAO, TaskScheduler taskScheduler) {
    this.itemDAO = itemDAO
    taskScheduler.scheduleWithFixedDelay(this, itemDAO.getHealthIntervalMillis())
  }

  @Override
  Health health() {
    if (!lastHealth.get()) {
      return new Health.Builder().down().build()
    }
    return lastHealth.get()
  }

  void run() {
    def healthBuilder = new Health.Builder().up()

    try {
      if (itemDAO.healthy) {
        healthBuilder.withDetail(itemDAO.class.simpleName, "Healthy")
      } else {
        healthBuilder.down().withDetail(itemDAO.class.simpleName, "Unhealthy")
      }
    } catch (RuntimeException e) {
      log.error("ItemDAO {} health check failed", itemDAO.class.simpleName, e)
      healthBuilder.down().withDetail(itemDAO.class.simpleName, "Unhealthy: `${e.message}`" as String)
    }

    lastHealth.set(healthBuilder.build())
  }
}
