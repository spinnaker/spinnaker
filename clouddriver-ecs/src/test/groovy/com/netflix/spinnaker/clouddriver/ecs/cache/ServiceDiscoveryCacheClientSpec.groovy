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

package com.netflix.spinnaker.clouddriver.ecs.cache

import com.amazonaws.services.servicediscovery.model.ServiceSummary
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceDiscoveryCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.model.ServiceDiscoveryRegistry
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ServiceDiscoveryCachingAgent
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICE_DISCOVERY_REGISTRIES

class ServiceDiscoveryCacheClientSpec extends Specification {
  def cacheView = Mock(Cache)
  @Subject
  private final ServiceDiscoveryCacheClient client = new ServiceDiscoveryCacheClient(cacheView)

  def 'should convert'() {
    given:
    def account = 'test-account'
    def region = 'us-west-1'
    def serviceName = 'my-service'
    def serviceId = 'srv-123'
    def serviceArn = 'arn:aws:servicediscovery:us-west-1:123456789012:service/srv-123'
    def key = Keys.getServiceDiscoveryRegistryKey('test-account', 'us-west-1', serviceId)

    def originalService = new ServiceDiscoveryRegistry(
      account: account,
      region: region,
      name: serviceName,
      arn: serviceArn,
      id: serviceId
    )

    def originalServiceEntry = new ServiceSummary(
      name: serviceName,
      id: serviceId,
      arn: serviceArn
    );

    def attributes = ServiceDiscoveryCachingAgent.convertServiceToAttributes(account, region, originalServiceEntry)
    cacheView.get(SERVICE_DISCOVERY_REGISTRIES.ns, key) >> new DefaultCacheData(key, attributes, Collections.emptyMap())

    when:
    def retrievedService = client.get(key)

    then:
    retrievedService == originalService
  }
}
