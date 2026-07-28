/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.agent

import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient
import software.amazon.awssdk.services.servicediscovery.model.ListServicesResponse
import software.amazon.awssdk.services.servicediscovery.model.ServiceSummary
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.model.ServiceDiscoveryRegistry
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICE_DISCOVERY_REGISTRIES

class ServiceDiscoveryCachingAgentSpec extends Specification {
  def serviceDiscovery = Mock(ServiceDiscoveryClient)
  def clientProvider = Mock(AmazonClientProvider)
  def providerCache = Mock(ProviderCache)

  @Subject
  ServiceDiscoveryCachingAgent agent = new ServiceDiscoveryCachingAgent(CommonCachingAgent.netflixAmazonCredentials, 'us-west-1', clientProvider)

  def 'should get a list of service discovery registries'() {
    given:
    def givenServices = []
    0.upto(4, {
      def serviceName = "test-service-${it}"
      def serviceId = "srv-${it}"
      givenServices << ServiceSummary.builder()
        .name(serviceName)
        .id(serviceId)
        .arn("arn:aws:servicediscovery:us-west-1:0123456789012:service/${serviceId}")
        .build()
    })
    serviceDiscovery.listServices(_) >> ListServicesResponse.builder().services(givenServices).build()

    when:
    def retrievedServices = agent.fetchServices(serviceDiscovery)

    then:
    retrievedServices.containsAll(givenServices)
    givenServices.containsAll(retrievedServices)
  }

  def 'should generate fresh data'() {
    given:
    Set givenServices = []
    Set servicesEntries = []
    0.upto(4, {
      def serviceName = "test-service-${it}"
      def serviceId = "srv-${it}"
      givenServices << new ServiceDiscoveryRegistry(
        account: 'test-account',
        region: 'us-west-1',
        name: serviceName,
        id: serviceId,
        arn: "arn:aws:servicediscovery:us-west-1:0123456789012:service/${serviceId}"
      )
      servicesEntries << ServiceSummary.builder()
        .name(serviceName)
        .id(serviceId)
        .arn("arn:aws:servicediscovery:us-west-1:0123456789012:service/${serviceId}")
        .build()
    })

    when:
    def cacheData = agent.generateFreshData(servicesEntries)

    then:
    cacheData.size() == 1
    cacheData.get(SERVICE_DISCOVERY_REGISTRIES.ns).size() == givenServices.size()
    givenServices*.account.containsAll(cacheData.get(SERVICE_DISCOVERY_REGISTRIES.ns)*.getAttributes().account)
    givenServices*.region.containsAll(cacheData.get(SERVICE_DISCOVERY_REGISTRIES.ns)*.getAttributes().region)
    givenServices*.name.containsAll(cacheData.get(SERVICE_DISCOVERY_REGISTRIES.ns)*.getAttributes().serviceName)
    givenServices*.arn.containsAll(cacheData.get(SERVICE_DISCOVERY_REGISTRIES.ns)*.getAttributes().serviceArn)
    givenServices*.id.containsAll(cacheData.get(SERVICE_DISCOVERY_REGISTRIES.ns)*.getAttributes().serviceId)
  }

  def 'should use filterIdentifiers with account and region glob for evictions'() {
    given:
    def givenService = ServiceSummary.builder()
      .name("test-service")
      .id("srv-123")
      .arn("arn:aws:servicediscovery:us-west-1:0123456789012:service/srv-123")
      .build()
    clientProvider.getAmazonServiceDiscoveryV2(_, _) >> serviceDiscovery
    serviceDiscovery.listServices(_) >> ListServicesResponse.builder().services([givenService]).build()

    def account = 'test-account'
    def region = 'us-west-1'
    def expectedGlob = com.netflix.spinnaker.clouddriver.ecs.cache.Keys.buildGlob(SERVICE_DISCOVERY_REGISTRIES, account, region)
    def oldIdentifiers = ['ecs;service-discovery-registries;test-account;us-west-1;old-service']
    providerCache.filterIdentifiers(SERVICE_DISCOVERY_REGISTRIES.ns, expectedGlob) >> oldIdentifiers

    when:
    def result = agent.loadData(providerCache)

    then:
    result.evictions[SERVICE_DISCOVERY_REGISTRIES.ns] != null
    result.evictions[SERVICE_DISCOVERY_REGISTRIES.ns].containsAll(oldIdentifiers)
  }
}
