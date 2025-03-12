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

package com.netflix.spinnaker.clouddriver.ecs.provider.view

import com.amazonaws.services.servicediscovery.model.ServiceSummary
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys
import com.netflix.spinnaker.clouddriver.ecs.cache.model.ServiceDiscoveryRegistry
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ServiceDiscoveryCachingAgent
import spock.lang.Specification
import spock.lang.Subject

class EcsServiceDiscoveryProviderSpec extends Specification {
  private static String ACCOUNT = 'test-account'
  private static String REGION = 'us-west-1'

  private Cache cacheView = Mock(Cache)
  @Subject
  private EcsServiceDiscoveryProvider serviceDiscoveryProvider = new EcsServiceDiscoveryProvider(cacheView)

  def 'should get no registries'() {
    given:
    cacheView.getAll(_) >> Collections.emptySet()

    when:
    def services = serviceDiscoveryProvider.getAllServiceDiscoveryRegistries()

    then:
    services.size() == 0
  }

  def 'should get a registry'() {
    given:
    def serviceName = 'my-service'
    def serviceId = 'srv-123'
    def serviceArn = "arn:aws:servicediscovery:" + REGION + ":012345678910:service/" + serviceId
    def key = Keys.getServiceDiscoveryRegistryKey(ACCOUNT, REGION, serviceId)

    HashSet keys = [key]

    ServiceSummary serviceEntry = new ServiceSummary(
      name: serviceName,
      arn: serviceArn,
      id: serviceId
    )

    def attributes = ServiceDiscoveryCachingAgent.convertServiceToAttributes(ACCOUNT, REGION, serviceEntry)
    def cacheData = new HashSet()
    cacheData.add(new DefaultCacheData(key, attributes, Collections.emptyMap()))

    cacheView.getAll(_) >> cacheData

    when:
    Collection<ServiceDiscoveryRegistry> services = serviceDiscoveryProvider.getAllServiceDiscoveryRegistries()

    then:
    services.size() == 1
    services[0].getName() == serviceName
    services[0].getId() == serviceId
    services[0].getArn() == serviceArn
  }

  def 'should get multiple services'() {
    given:
    int numberOfServices = 5
    Set<String> serviceIds = new HashSet<>()
    Collection<CacheData> cacheData = new HashSet<>()
    Set<String> keys = new HashSet<>()

    numberOfServices.times { x ->
      String serviceName = "test-service-" + x
      String serviceId = "srv-" + x
      String serviceArn = "arn:aws:servicediscovery:" + REGION + ":012345678910:service/" + serviceId
      String key = Keys.getServiceDiscoveryRegistryKey(ACCOUNT, REGION, serviceId)

      keys.add(key)
      serviceIds.add(serviceId)

      ServiceSummary serviceEntry = new ServiceSummary(
        name: serviceName,
        arn: serviceArn,
        id: serviceId
      )

      Map<String, Object> attributes = ServiceDiscoveryCachingAgent.convertServiceToAttributes(ACCOUNT, REGION, serviceEntry)
      cacheData.add(new DefaultCacheData(key, attributes, Collections.emptyMap()))
    }

    cacheView.getAll(_) >> cacheData

    when:
    Collection<ServiceDiscoveryRegistry> services = serviceDiscoveryProvider.getAllServiceDiscoveryRegistries()

    then:
    services.size() == numberOfServices
    serviceIds.containsAll(services*.getId())
    services*.getId().containsAll(serviceIds)
  }
}
