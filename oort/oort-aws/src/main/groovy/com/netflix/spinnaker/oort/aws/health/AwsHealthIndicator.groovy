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


package com.netflix.spinnaker.oort.aws.health

import com.amazonaws.AmazonServiceException
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
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
class AwsHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(AwsHealthIndicator)

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AmazonClientProvider amazonClientProvider

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
      Set<NetflixAmazonCredentials> amazonCredentials = accountCredentialsProvider.all.findAll {
        it instanceof NetflixAmazonCredentials
      } as Set<NetflixAmazonCredentials>
      if (!amazonCredentials) {
        throw new AmazonCredentialsNotFoundException()
      }
      for (NetflixAmazonCredentials credentials in amazonCredentials) {
        try {
          def ec2 = amazonClientProvider.getAmazonEC2(credentials, "us-east-1")
          ec2.describeAccountAttributes()
        } catch (AmazonServiceException e) {
          throw new AmazonUnreachableException(e)
        }
      }
      lastException.set(null)
    } catch (Exception ex) {
      LOG.warn "Unhealthy", ex
      lastException.set(ex)
    }
  }

  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = 'AWS Module is configured, but no credentials found.')
  @InheritConstructors
  static class AmazonCredentialsNotFoundException extends RuntimeException {}

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = 'Could not reach Amazon.')
  @InheritConstructors
  static class AmazonUnreachableException extends RuntimeException {}
}