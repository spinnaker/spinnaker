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

import com.amazonaws.AmazonServiceException
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.front50.model.application.ApplicationDAOProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import java.util.concurrent.atomic.AtomicReference

@Component
public class ApplicationDAOProviderHealthIndicator implements HealthIndicator {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  List<ApplicationDAOProvider> applicationDAOProviders

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

    for (account in accountCredentialsProvider.all) {
      applicationDAOProviders.findAll { it.supports(account.getClass()) }.each { provider ->
        def providerId = "${provider.class.simpleName}-${account.name}"
        try {
          if (provider.getForAccount(account).healthly) {
            healthBuilder.withDetail(providerId, "Healthy")
          } else {
            healthBuilder.down().withDetail(providerId, "Unhealthy")
          }
        } catch (RuntimeException e) {
          healthBuilder.down().withDetail(providerId, "Unhealthy: `${e.message}`" as String)
        }
      }
    }

    lastHealth.set(healthBuilder.build())
  }
}
