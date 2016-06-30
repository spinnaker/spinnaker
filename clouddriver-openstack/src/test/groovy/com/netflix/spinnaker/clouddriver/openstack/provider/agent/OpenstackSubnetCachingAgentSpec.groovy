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
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.network.Subnet
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SUBNETS

class OpenstackSubnetCachingAgentSpec extends Specification {

  OpenstackSubnetCachingAgent cachingAgent
  OpenstackNamedAccountCredentials namedAccountCredentials
  ObjectMapper objectMapper
  final String region = 'east'
  final String account = 'account'

  void "setup"() {
    namedAccountCredentials = GroovyMock(OpenstackNamedAccountCredentials)
    objectMapper = Mock(ObjectMapper)
    cachingAgent = Spy(OpenstackSubnetCachingAgent, constructorArgs: [namedAccountCredentials, region, objectMapper])
  }

  void "test load data"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    OpenstackCredentials credentials = GroovyMock()
    OpenstackClientProvider provider = Mock()
    Subnet subnet = Mock(Subnet)
    String subnetId = UUID.randomUUID().toString()
    Map<String, Object> subnetAttributes = Mock(Map)
    String subnetKey = Keys.getSubnetKey(subnetId, region, account)

    when:
    CacheResult result = cachingAgent.loadData(providerCache)

    then:
    1 * namedAccountCredentials.credentials >> credentials
    _ * cachingAgent.getAccountName() >> account
    1 * credentials.provider >> provider
    1 * provider.listSubnets(region) >> [subnet]
    _ * subnet.id >> subnetId
    1 * objectMapper.convertValue(_, OpenstackInfrastructureProvider.ATTRIBUTES) >> subnetAttributes

    and:
    result.cacheResults.get(SUBNETS.ns).first().id == subnetKey
    result.cacheResults.get(SUBNETS.ns).first().attributes == subnetAttributes
    noExceptionThrown()
  }

  void "test load data exception"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    OpenstackCredentials credentials = GroovyMock()
    OpenstackClientProvider provider = Mock()
    Throwable throwable = new OpenstackProviderException(ActionResponse.actionFailed('test', 1))

    when:
    cachingAgent.loadData(providerCache)

    then:
    1 * namedAccountCredentials.credentials >> credentials
    1 * credentials.provider >> provider
    1 * provider.listSubnets(region) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException == throwable
  }
}
