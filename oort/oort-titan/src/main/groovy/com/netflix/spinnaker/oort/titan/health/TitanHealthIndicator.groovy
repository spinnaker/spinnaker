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


package com.netflix.spinnaker.oort.titan.health
import com.netflix.spinnaker.oort.titan.TitanClientProvider
import com.netflix.spinnaker.oort.titan.credentials.config.CredentialsConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import java.util.concurrent.atomic.AtomicReference

@Component
class TitanHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(TitanHealthIndicator)

  private final CredentialsConfig titanConfig
  private final TitanClientProvider titanClientProvider
  private AtomicReference<Health> health = new AtomicReference<>(new Health.Builder().up().build())

  @Autowired
  TitanHealthIndicator(CredentialsConfig titanConfig, TitanClientProvider titanClientProvider) {
    this.titanConfig = titanConfig
    this.titanClientProvider = titanClientProvider
  }

  @Override
  Health health() {
    health.get()
  }

  @Scheduled(fixedDelay = 300000L)
  void checkHealth() {
    // TODO
    /*for (CredentialsConfig.Account account in titanConfig.accounts) {
      for (CredentialsConfig.Region region in account.regions) {
        try {
          TitanHealth titanHealth = titanClientProvider.getTitanClient(account.name, region.name).getHealth()
          titanHealth.healthStatus == HealthStatus.UNHEALTHY
        } catch (e) {
          new Health.Builder().outOfService().withException(new TitanUnreachableException(e))
          throw new TitanUnreachableException(e)
        }
      }
    }*/
  }

}