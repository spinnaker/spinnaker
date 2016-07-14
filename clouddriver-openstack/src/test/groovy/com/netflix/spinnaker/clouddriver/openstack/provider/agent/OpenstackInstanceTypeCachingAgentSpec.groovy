/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackInstanceType
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.compute.Flavor
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCE_TYPES

class OpenstackInstanceTypeCachingAgentSpec extends Specification {

  OpenstackInstanceTypeCachingAgent cachingAgent
  OpenstackNamedAccountCredentials namedAccountCredentials
  OpenstackClientProvider provider
  String region = 'east'
  String account = 'test'
  ObjectMapper objectMapper

  void "setup"() {
    namedAccountCredentials = Mock(OpenstackNamedAccountCredentials)
    provider = Mock(OpenstackClientProvider)
    objectMapper = new ObjectMapper()
    cachingAgent = Spy(OpenstackInstanceTypeCachingAgent, constructorArgs: [namedAccountCredentials, region, objectMapper]) {
      getAccountName() >> account
      getClientProvider() >> provider
    }
  }

  void "test load data"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String flavorId = UUID.randomUUID().toString()

    and:
    Flavor flavor = Mock(Flavor) {
      getId() >> { flavorId }
    }
    OpenstackInstanceType openstackInstanceType = OpenstackInstanceType.builder().region(region).account(account).id(flavorId).build()
    Map<String, Object> instanceTypesAttributes = objectMapper.convertValue(openstackInstanceType, OpenstackInfrastructureProvider.ATTRIBUTES)

    and:
    String instanceTypeKey = Keys.getInstanceTypeKey(flavorId, account, region)

    when:
    CacheResult result = cachingAgent.loadData(providerCache)

    then:
    1 * provider.listFlavors(region) >> [flavor]

    and:
    result.cacheResults != null
    noExceptionThrown()

    and:
    Collection<CacheData> instanceTypesData = result.cacheResults.get(INSTANCE_TYPES.ns)
    instanceTypesData.size() == 1
    instanceTypesData.first().id == instanceTypeKey
    instanceTypesData.first().attributes == instanceTypesAttributes
    instanceTypesData.first().relationships.isEmpty()
  }

  void "test load data exception"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    Throwable throwable = new OpenstackProviderException(ActionResponse.actionFailed('test', 1))

    when:
    cachingAgent.loadData(providerCache)

    then:
    1 * provider.listFlavors(region) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException == throwable
  }
}
