/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry.health

import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryCredentials
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.transform.InheritConstructors
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus
import retrofit.RetrofitError

import java.util.concurrent.atomic.AtomicReference

@Component
class DockerRegistryHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(DockerRegistryHealthIndicator)

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  private final AtomicReference<Exception> lastException = new AtomicReference<>(null)

  @Override
  Health health() {
    def ex = lastException.get()

    if (ex) {
      throw ex
    }

    new Health.Builder().up().build()
  }

  @Scheduled(fixedDelay = 300000L)
  void checkHealth() {
    try {
      Set<DockerRegistryNamedAccountCredentials> dockerRegistryCredentialsSet = accountCredentialsProvider.all.findAll {
        it instanceof DockerRegistryNamedAccountCredentials
      } as Set<DockerRegistryNamedAccountCredentials>

      for (DockerRegistryNamedAccountCredentials accountCredentials in dockerRegistryCredentialsSet) {
        DockerRegistryCredentials dockerRegistryCredentials = accountCredentials.credentials

        if (!dockerRegistryCredentials.client.isV2()) {
          throw new IllegalArgumentException("The docker registry ${dockerRegistryCredentials.client.address} must have a valid /v2/ endpoint.")
        }
      }

      lastException.set(null)
    } catch (Exception ex) {
      LOG.warn "Unhealthy", ex

      lastException.set(ex)
    }
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Problem communicating with DockerRegistry.")
  @InheritConstructors
  static class DockerRegistryIOException extends RuntimeException {}
}

