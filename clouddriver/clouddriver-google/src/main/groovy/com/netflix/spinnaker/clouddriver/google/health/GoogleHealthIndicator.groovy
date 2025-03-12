/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.health

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.google.GoogleExecutorTraits
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.credentials.CredentialsRepository
import com.netflix.spinnaker.credentials.CredentialsTypeBaseConfiguration
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

import java.util.concurrent.atomic.AtomicReference

@Component
class GoogleHealthIndicator implements HealthIndicator, GoogleExecutorTraits {

  private static final Logger LOG = LoggerFactory.getLogger(GoogleHealthIndicator)

  @Autowired
  Registry registry

  @Autowired
  CredentialsTypeBaseConfiguration<GoogleNamedAccountCredentials, ?> credentialsTypeBaseConfiguration

  private final AtomicReference<Exception> lastException = new AtomicReference<>(null)

  @Autowired
  GoogleConfigurationProperties googleConfigurationProperties

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
      if (googleConfigurationProperties.getHealth().getVerifyAccountHealth()) {
        LOG.info("google.health.verifyAccountHealth flag is enabled - verifying connection to the Google accounts")
        credentialsTypeBaseConfiguration.credentialsRepository?.all?.forEach({
          try {
            timeExecute(it.compute.projects().get(it.project),
              "compute.projects.get",
              TAG_SCOPE, SCOPE_GLOBAL)
          } catch (IOException e) {
            throw new GoogleIOException(e)
          }
        })
      } else {
        LOG.info("google.health.verifyAccountHealth flag is disabled - Not verifying connection to the Google accounts");
      }
      lastException.set(null)
    } catch (Exception ex) {
      LOG.warn "Unhealthy", ex

      lastException.set(ex)
    }
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Problem communicating with Google.")
  @InheritConstructors
  static class GoogleIOException extends RuntimeException {}
}

