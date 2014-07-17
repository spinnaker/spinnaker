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


package com.netflix.spinnaker.kato.health

import com.amazonaws.AmazonServiceException
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.security.NamedAccountCredentials
import com.netflix.spinnaker.kato.security.NamedAccountCredentialsHolder
import com.netflix.spinnaker.kato.security.aws.AmazonRoleAccountCredentials
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

@Component
class AmazonHealthIndicator implements HealthIndicator {

  @Autowired
  NamedAccountCredentialsHolder namedAccountCredentialsHolder

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  Health health() {
    List<NamedAccountCredentials> amazonCredentials = namedAccountCredentialsHolder.accountNames.collect {
      namedAccountCredentialsHolder.getCredentials(it)
    }.findAll { it instanceof AmazonRoleAccountCredentials }
    if (!amazonCredentials) {
      throw new AmazonCredentialsNotFoundException()
    }
    for (NamedAccountCredentials<AmazonCredentials> namedAccountCredentials in amazonCredentials) {
      try {
        def ec2 = amazonClientProvider.getAmazonEC2(namedAccountCredentials.credentials, "us-east-1")
        ec2.describeAccountAttributes()
      } catch (AmazonServiceException e) {
        throw new AmazonUnreachableException(e)
      }
    }
    new Health.Builder().up().build()
  }

  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = 'AWS Module is configured, but no credentials found.')
  @InheritConstructors
  static class AmazonCredentialsNotFoundException extends RuntimeException {}

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = 'Could not reach Amazon.')
  @InheritConstructors
  static class AmazonUnreachableException extends RuntimeException {}
}
