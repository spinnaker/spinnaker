/*
 * Copyright 2017 Cerner Corporation
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

package com.netflix.spinnaker.clouddriver.dcos.health

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.security.DcosClusterCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.transform.InheritConstructors
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.bind.annotation.ResponseStatus

import java.util.concurrent.atomic.AtomicReference

class DcosHealthIndicator implements HealthIndicator {
  private final AccountCredentialsProvider accountCredentialsProvider
  private final DcosClientProvider dcosClientProvider
  private final AtomicReference<Exception> lastException = new AtomicReference<>(null)

  DcosHealthIndicator(AccountCredentialsProvider accountCredentialsProvider,
                      DcosClientProvider dcosClientProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider
    this.dcosClientProvider = dcosClientProvider
  }

  @Override
  Health health() {
    def ex = lastException.get()

    if (ex) {
      new Health.Builder().down().build()
    }

    new Health.Builder().up().build()
  }

  @Scheduled(fixedDelay = 300000L)
  void checkHealth() {
    try {
      Set<DcosAccountCredentials> dcosCredentialsSet = accountCredentialsProvider.all.findAll {
        it instanceof DcosAccountCredentials
      } as Set<DcosAccountCredentials>

      for (DcosAccountCredentials accountCredentials in dcosCredentialsSet) {
        for (DcosClusterCredentials dcosClusterCredentials in accountCredentials.credentials.credentials) {
          String pong = dcosClientProvider.getDcosClient(dcosClusterCredentials).ping

          if ("pong" != pong) {
            throw new DcosIOException()
          }
        }
      }

      lastException.set(null)
    } catch (Exception ex) {
      lastException.set(ex)
    }
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Problem communicating with DCOS.")
  @InheritConstructors
  static class DcosIOException extends RuntimeException {}
}

