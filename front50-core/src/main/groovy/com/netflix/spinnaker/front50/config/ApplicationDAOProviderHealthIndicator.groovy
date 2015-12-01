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

import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import java.util.concurrent.atomic.AtomicReference

@Component
public class ApplicationDAOProviderHealthIndicator implements HealthIndicator {

  @Autowired
  ApplicationDAO applicationDAO

  private final AtomicReference<Health> lastHealth = new AtomicReference<>(null)

  @Override
  public Health health() {
    if (!lastHealth.get()) {
      return new Health.Builder().down().build()
    }
    return lastHealth.get()
  }

  @Scheduled(fixedDelay = 30000L)
  void pollForHealth() {
    def healthBuilder = new Health.Builder().up()

    try {
      if (applicationDAO.healthy) {
        healthBuilder.withDetail(applicationDAO.class.simpleName, "Healthy")
      } else {
        healthBuilder.down().withDetail(applicationDAO.class.simpleName, "Unhealthy")
      }
    } catch (RuntimeException e) {
      healthBuilder.down().withDetail(applicationDAO.class.simpleName, "Unhealthy: `${e.message}`" as String)
    }

    lastHealth.set(healthBuilder.build())
  }
}
