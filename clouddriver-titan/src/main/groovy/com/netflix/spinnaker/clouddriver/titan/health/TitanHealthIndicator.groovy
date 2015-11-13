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

package com.netflix.spinnaker.clouddriver.titan.health

import com.netflix.spinnaker.clouddriver.titan.TitanClientProvider
import com.netflix.spinnaker.clouddriver.titan.credentials.NetflixTitanCredentials
import com.netflix.titanclient.TitanRegion
import com.netflix.titanclient.model.HealthStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import java.util.concurrent.atomic.AtomicReference

@Component
class TitanHealthIndicator implements HealthIndicator {

  private final List<NetflixTitanCredentials> credentialsList
  private final TitanClientProvider titanClientProvider
  private AtomicReference<Health> health = new AtomicReference<>(new Health.Builder().up().build())

  @Autowired
  TitanHealthIndicator(@Value('#{netflixTitanCredentials}') List<NetflixTitanCredentials> credentialsList,
                       TitanClientProvider titanClientProvider) {
    this.credentialsList = credentialsList
    this.titanClientProvider = titanClientProvider
  }

  @Override
  Health health() {
    health.get()
  }

  @Scheduled(fixedDelay = 300000L)
  void checkHealth() {
    Status status = Status.UP
    Map<String, Object> details = [:]
    for (NetflixTitanCredentials account in credentialsList) {
      for (TitanRegion region in account.regions) {
        Status regionStatus
        Map regionDetails = [:]
        try {
          HealthStatus health = titanClientProvider.getTitanClient(account, region.name).getHealth().healthStatus
          regionStatus = health == HealthStatus.UNHEALTHY ? Status.OUT_OF_SERVICE : Status.UP
        } catch (e) {
          regionStatus = Status.OUT_OF_SERVICE
          regionDetails << [reason: e]
        }
        regionDetails << [status: regionStatus]
        if (regionStatus == Status.OUT_OF_SERVICE) {
          status = Status.OUT_OF_SERVICE
        }
        details << [("${account.name}:${region.name}"): regionDetails]
      }
    }
    health.set(new Health.Builder(status, details).build())
  }

}
