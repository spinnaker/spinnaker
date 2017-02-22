/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.aws.lifecycle

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery.AwsEurekaSupport
import com.netflix.spinnaker.clouddriver.aws.provider.AwsCleanupProvider
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification
import spock.lang.Subject

import javax.inject.Provider

class InstanceTerminationLifecycleAgentProviderSpec extends Specification {

  ObjectMapper objectMapper = Mock()
  AmazonClientProvider amazonClientProvider = Mock()
  AccountCredentialsProvider accountCredentialsProvider = Mock()
  Provider<AwsEurekaSupport> awsEurekaSupport = Mock()
  Registry registry = Mock()

  InstanceTerminationConfigurationProperties properties = new InstanceTerminationConfigurationProperties(
    'mgmt',
    'arn:aws:sqs:{{region}}:{{accountId}}:queueName',
    'arn:aws:sqs:{{region}}:{{accountId}}:topicName',
    -1,
    -1,
    -1
  )

  @Subject
  def subject = new InstanceTerminationLifecycleAgentProvider(
    objectMapper,
    amazonClientProvider,
    accountCredentialsProvider,
    properties,
    awsEurekaSupport,
    registry
  )

  def 'should support AwsProvider'() {
    expect:
    subject.supports(AwsProvider.PROVIDER_NAME)
    !subject.supports(AwsCleanupProvider.PROVIDER_NAME)
  }

  def 'should return an agent per region in specified account'() {
    given:
    def regions = ['us-west-1', 'us-west-2', 'us-east-1']
    def mgmtCredentials = Mock(NetflixAmazonCredentials) {
      getRegions() >> {
        return regions.collect { new AmazonCredentials.AWSRegion(it, []) }
      }
      getAccountId() >> "100"
      getName() >> "mgmt"
    }

    when:
    def agents = subject.agents()

    then:
    regions.each { String region ->
      assert agents.find { it.agentType == "mgmt/${region}/InstanceTerminationLifecycleAgent".toString() } != null
    }
    1 * accountCredentialsProvider.getCredentials("mgmt") >> mgmtCredentials
    3 * accountCredentialsProvider.getAll() >> [mgmtCredentials]
  }
}
