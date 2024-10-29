/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.health

import com.netflix.spinnaker.clouddriver.azure.config.AzureConfigurationProperties
import com.netflix.spinnaker.clouddriver.azure.security.AzureNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.transform.CompileStatic
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
@CompileStatic
class AzureHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(AzureHealthIndicator)

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AzureConfigurationProperties azureConfigurationProperties

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
      if (azureConfigurationProperties.getHealth().getVerifyAccountHealth()) {
        LOG.info("azure.health.verifyAccountHealth flag is enabled - verifying connection to the Azure accounts")
      Set<AzureNamedAccountCredentials> azureCredentialsSet = accountCredentialsProvider.all.findAll {
        it instanceof AzureNamedAccountCredentials
      } as Set<AzureNamedAccountCredentials>

      if (!azureCredentialsSet) {
        throw new AzureCredentialsNotFoundException()
      }

      for (AzureNamedAccountCredentials accountCredentials in azureCredentialsSet) {
        try {
          // This verifies that the specified credentials are sufficient to access the referenced project.
          accountCredentials.credentials.resourceManagerClient.healthCheck()
        } catch (IOException e) {
          throw new AzureIOException(e)
        }
      }
      } else {
        LOG.info("azure.health.verifyAccountHealth flag is disabled - Not verifying connection to the Azure accounts");
      }
      lastException.set(null)
    } catch (Exception ex) {
      LOG.warn "Unhealthy", ex

      lastException.set(ex)
    }
  }

  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = 'Azure Module is configured, but no credentials found.')
  @InheritConstructors
  static class AzureCredentialsNotFoundException extends RuntimeException {}

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Problem communicating with Azure.")
  @InheritConstructors
  static class AzureIOException extends RuntimeException {}
}

