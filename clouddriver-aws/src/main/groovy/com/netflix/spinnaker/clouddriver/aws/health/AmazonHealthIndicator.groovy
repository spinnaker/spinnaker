/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.health

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AmazonEC2Exception
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
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

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Component
class AmazonHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(AmazonHealthIndicator)

  private final AccountCredentialsProvider accountCredentialsProvider
  private final AmazonClientProvider amazonClientProvider

  private final AtomicReference<Exception> lastException = new AtomicReference<>(null)
  private final AtomicReference<Boolean> hasInitialized = new AtomicReference<>(null)

  private final AtomicLong errors;

  @Autowired
  AmazonHealthIndicator(AccountCredentialsProvider accountCredentialsProvider,
                        AmazonClientProvider amazonClientProvider,
                        Registry registry) {
    this.accountCredentialsProvider = accountCredentialsProvider
    this.amazonClientProvider = amazonClientProvider

    this.errors = registry.gauge("health.amazon.errors", new AtomicLong(0))
  }

  @Override
  Health health() {
    if (hasInitialized.get() == Boolean.TRUE) {
      // avoid being marked unhealthy once connectivity to all accounts has been verified at least once
      return new Health.Builder().up().build()
    }

    def ex = lastException.get()
    if (ex) {
      throw ex
    }

    return new Health.Builder().unknown().build()
  }

  @Scheduled(fixedDelay = 120000L)
  void checkHealth() {
    try {
      Set<NetflixAmazonCredentials> amazonCredentials = accountCredentialsProvider.all.findAll {
        it instanceof NetflixAmazonCredentials
      } as Set<NetflixAmazonCredentials>
      for (NetflixAmazonCredentials credentials in amazonCredentials) {
        try {
          AmazonEC2 ec2 = amazonClientProvider.getAmazonEC2(credentials, AmazonClientProvider.DEFAULT_REGION, true)
          if (!ec2) {
            throw new AmazonClientException("Could not create Amazon client for ${credentials.name}")
          }
          ec2.describeAccountAttributes()
        } catch (AmazonServiceException e) {
          if (!e.errorCode?.equalsIgnoreCase("RequestLimitExceeded")) {
            throw new AmazonUnreachableException("Failed to describe account attributes for '${credentials.name}'", e)
          }
        }
      }
      hasInitialized.set(Boolean.TRUE)
      lastException.set(null)
      errors.set(0)
    } catch (Exception ex) {
      LOG.error "Unhealthy", ex
      lastException.set(ex)
      errors.set(1)
    }
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = 'Could not reach Amazon.')
  @InheritConstructors
  static class AmazonUnreachableException extends RuntimeException {}
}
