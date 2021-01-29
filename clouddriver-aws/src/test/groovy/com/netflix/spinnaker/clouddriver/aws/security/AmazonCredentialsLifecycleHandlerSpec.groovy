/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AccountAttribute
import com.amazonaws.services.ec2.model.AccountAttributeValue
import com.amazonaws.services.ec2.model.DescribeAccountAttributesResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.cats.agent.AgentProvider
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.AwsConfigurationProperties
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApiFactory
import com.netflix.spinnaker.clouddriver.aws.provider.AwsCleanupProvider
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ImageCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ReservationReportCachingAgent
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.credentials.CredentialsRepository
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.stream.Collectors

class AmazonCredentialsLifecycleHandlerSpec extends Specification {
  AwsCleanupProvider awsCleanupProvider
  AwsInfrastructureProvider awsInfrastructureProvider
  AwsProvider awsProvider
  Optional<Collection<AgentProvider>> agentProviders = Optional.empty()
  def amazonCloudProvider = new AmazonCloudProvider()
  def registry = new DefaultRegistry()
  def eddaApiFactory = new EddaApiFactory()
  def dynamicConfigService = Mock(DynamicConfigService) {
    isEnabled("aws.features.cloud-formation", false) >> false
    isEnabled("aws.features.launch-templates", false) >> false
  }
  def objectMapper = new ObjectMapper()
  def credOne = TestCredential.named('one')
  def credTwo = TestCredential.named('two')
  def credThree = TestCredential.named('three')
  def credentialsRepository = Mock(CredentialsRepository) {
    getAll() >> [credOne, credTwo]
  }
  Optional<ExecutorService> reservationReportPool = Optional.of(
    Mock(ExecutorService)
  )
  def deployDefaults = new  AwsConfiguration.DeployDefaults()

  def awsConfigurationProperties = new AwsConfigurationProperties()

  def setup() {
    awsCleanupProvider = new AwsCleanupProvider()
    awsInfrastructureProvider = new AwsInfrastructureProvider()
    awsProvider = new AwsProvider(credentialsRepository)
  }


  def 'it should replace current public image caching agent'() {
    def imageCachingAgentOne = new ImageCachingAgent(null, credOne, "us-east-1", objectMapper, null, true, null)
    def imageCachingAgentTwo = new ImageCachingAgent(null, credTwo, "us-east-1", objectMapper, null, false, null)
    awsProvider.addAgents([imageCachingAgentOne, imageCachingAgentTwo])
    def handler = new AmazonCredentialsLifecycleHandler(awsCleanupProvider, awsInfrastructureProvider, awsProvider,
      null, null, null, null, objectMapper, null, null, null, null, null, null, null, null, null, null,
      credentialsRepository)

    when:
    handler.credentialsDeleted(credOne)

    then:
    awsProvider.getAgents().stream()
      .filter({ agent -> agent.handlesAccount("two")})
      .filter({ agent -> ((ImageCachingAgent) agent).getIncludePublicImages() })
      .collect(Collectors.toList()).size() == 1
  }

  def 'it should remove region not used by public image caching agent'() {
    def imageCachingAgentOne = new ImageCachingAgent(null, credOne, "us-west-2", objectMapper, null, true, null)
    def imageCachingAgentTwo = new ImageCachingAgent(null, credTwo, "us-east-1", objectMapper, null, false, null)
    awsProvider.addAgents([imageCachingAgentOne, imageCachingAgentTwo])
    def handler = new AmazonCredentialsLifecycleHandler(awsCleanupProvider, awsInfrastructureProvider, awsProvider,
      null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
      credentialsRepository)
    handler.publicRegions.add("us-west-2")

    when:
    handler.credentialsDeleted(credOne)

    then:
    !handler.publicRegions.contains("us-west-2")
  }

  def 'it should add agents'() {
    def amazonEC2 = Mock(AmazonEC2)
    def amazonClientProvider = Mock(AmazonClientProvider) {
      getAmazonEC2(_, _) >> amazonEC2
    }
    def handler = new AmazonCredentialsLifecycleHandler(awsCleanupProvider, awsInfrastructureProvider, awsProvider,
      amazonCloudProvider, amazonClientProvider, null, awsConfigurationProperties, objectMapper, null, eddaApiFactory, null, registry, reservationReportPool, agentProviders, null, null, dynamicConfigService, deployDefaults,
      credentialsRepository)
    def credThree = TestCredential.named('three')

    when:
    handler.credentialsAdded(credThree)

    then:
    awsInfrastructureProvider.getAgents().size() == 12
    awsProvider.getAgents().size() == 22
    handler.publicRegions.size() == 2
    handler.awsInfraRegions.size() == 2
    handler.reservationReportCachingAgentScheduled
    def reservationReportCachingAgent = awsProvider.getAgents().stream()
      .filter({ agent -> agent instanceof ReservationReportCachingAgent })
      .map({ agent -> (ReservationReportCachingAgent) agent })
      .findFirst().get()
  }

  def 'subsequent call should not add reservation caching agents'() {
    def handler = new AmazonCredentialsLifecycleHandler(awsCleanupProvider, awsInfrastructureProvider, awsProvider,
      amazonCloudProvider, null, null, awsConfigurationProperties, objectMapper, null, eddaApiFactory, null, registry, reservationReportPool, agentProviders, null, null, dynamicConfigService, deployDefaults,
      credentialsRepository)
    def credThree = TestCredential.named('three')
    handler.reservationReportCachingAgentScheduled = true

    when:
    handler.credentialsAdded(credThree)

    then:
    awsProvider.getAgents().stream().filter({ agent -> agent instanceof ReservationReportCachingAgent })
    .collect(Collectors.toList()).isEmpty()
    handler.reservationReportCachingAgentScheduled
  }

  def 'account should be removed from reservation agent'() {
    def amazonEC2 = Mock(AmazonEC2) {
      describeAccountAttributes(_) >> new DescribeAccountAttributesResult().withAccountAttributes(
        new AccountAttribute().withAttributeName("supported-platforms").withAttributeValues(
          new AccountAttributeValue().withAttributeValue("VPC")
        ))
    }
    def amazonClientProvider = Mock(AmazonClientProvider) {
      getAmazonEC2(_, _) >> amazonEC2
    }
    def handler = new AmazonCredentialsLifecycleHandler(awsCleanupProvider, awsInfrastructureProvider, awsProvider,
      amazonCloudProvider, amazonClientProvider, null, awsConfigurationProperties, objectMapper, null, eddaApiFactory, null, registry, reservationReportPool, agentProviders, null, null, dynamicConfigService, deployDefaults,
      credentialsRepository)
    def credThree = TestCredential.named('three')
    handler.credentialsAdded(credThree)

    when:
    handler.credentialsDeleted(credThree)

    then:
    handler.reservationReportCachingAgentScheduled
    def reservationReportCachingAgent = awsProvider.getAgents().stream()
      .filter({ agent -> agent instanceof ReservationReportCachingAgent })
      .map({ agent -> (ReservationReportCachingAgent) agent })
      .findFirst().get()
  }
}
