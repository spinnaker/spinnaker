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

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeAccountAttributesResult
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.aws.AwsConfigurationProperties
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.credentials.CredentialsRepository
import org.springframework.boot.actuate.health.Status
import spock.lang.Specification
import spock.lang.Unroll

class AmazonHealthIndicatorSpec extends Specification {

  private static final Registry REGISTRY = new NoopRegistry()
  AwsConfigurationProperties awsConfigurationProperties

  void setup(){
    awsConfigurationProperties = new AwsConfigurationProperties()
  }

  @Unroll
  def "health details contains warning when amazon appears unreachable"() {
    setup:
    def creds = [TestCredential.named('foo')]
    def credentialsRepository = Stub(CredentialsRepository) {
      getAll() >> creds
    }
    def mockEc2 = Stub(AmazonEC2) {
      describeAccountAttributes() >> { throw new AmazonServiceException("fail") }
    }
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonEC2(*_) >> mockEc2
    }

    awsConfigurationProperties.health.setVerifyAccountHealth(verifyAccountHealth)

    def indicator = new AmazonHealthIndicator(
      REGISTRY,
      credentialsRepository,
      mockAmazonClientProvider,
      awsConfigurationProperties)

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
    if (verifyAccountHealth) {
      (health.details['foo'] as String).startsWith("Failed to describe account attributes for 'foo'.")
    } else {
      health.details.isEmpty()
    }

    where:
    verifyAccountHealth | _
    true                | _
    false               | _
  }

  def "health succeeds when amazon is reachable"() {
    setup:
    def creds = [TestCredential.named('foo')]
    def credentialsRepository = Stub(CredentialsRepository) {
      getAll() >> creds
    }
    def mockEc2 = Stub(AmazonEC2) {
      describeAccountAttributes() >> { Mock(DescribeAccountAttributesResult) }
    }
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonEC2(*_) >> mockEc2
    }

    def indicator = new AmazonHealthIndicator(
      REGISTRY,
      credentialsRepository,
      mockAmazonClientProvider,
      awsConfigurationProperties
    )

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
    health.details.isEmpty()
  }

  def "health succeeds when no amazon accounts"() {
    setup:
    def credentialsRepository = Stub(CredentialsRepository) {
      getAll() >> []
    }
    def mockEc2 = Stub(AmazonEC2) {
      describeAccountAttributes() >> { Mock(DescribeAccountAttributesResult) }
    }
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonEC2(*_) >> mockEc2
    }

    def indicator = new AmazonHealthIndicator(
      REGISTRY,
      credentialsRepository,
      mockAmazonClientProvider,
      awsConfigurationProperties
    )

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
    health.details.isEmpty()
  }

  def "health details contains warnings when there are multiple errors"() {
    setup:
    def creds = [TestCredential.named('foo'), TestCredential.named('bar')]
    def credentialsRepository = Stub(CredentialsRepository) {
      getAll() >> creds
    }
    def mockEc2 = Stub(AmazonEC2) {
      describeAccountAttributes() >> { throw new AmazonServiceException("fail") }
    }

    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonEC2(*_) >> mockEc2
    }

    def indicator = new AmazonHealthIndicator(
      REGISTRY,
      credentialsRepository,
      mockAmazonClientProvider,
      awsConfigurationProperties
    )

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
    (health.details['foo'] as String).startsWith("Failed to describe account attributes for 'foo'.")
    (health.details['bar'] as String).startsWith("Failed to describe account attributes for 'bar'.")
  }
}
