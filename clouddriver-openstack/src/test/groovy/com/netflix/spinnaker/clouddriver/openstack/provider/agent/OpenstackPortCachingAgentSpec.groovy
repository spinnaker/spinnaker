/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
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
import org.openstack4j.model.network.Port
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.PORTS

class OpenstackPortCachingAgentSpec extends Specification {

  OpenstackPortCachingAgent cachingAgent
  OpenstackNamedAccountCredentials namedAccountCredentials
  OpenstackCredentials credentials
  ObjectMapper objectMapper
  final String region = 'east'
  final String account = 'account'

  void "setup"() {
    credentials = GroovyMock(OpenstackCredentials)
    namedAccountCredentials = GroovyMock(OpenstackNamedAccountCredentials) {
      it.credentials >> { credentials }
    }
    objectMapper = Mock(ObjectMapper)
    cachingAgent = Spy(OpenstackPortCachingAgent, constructorArgs: [namedAccountCredentials, region, objectMapper]) {
      it.accountName >> { account }
    }
  }

  void "test load data"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    OpenstackClientProvider provider = Mock(OpenstackClientProvider)
    String portId = UUID.randomUUID().toString()
    Port port = Mock(Port) {
      it.id >> { portId }
    }
    Map<String, Object> portAttributes = Mock(Map)
    String portKey = Keys.getPortKey(portId, account, region)

    when:
    CacheResult result = cachingAgent.loadData(providerCache)

    then:
    1 * credentials.provider >> provider
    1 * provider.listPorts(region) >> [port]
    1 * objectMapper.convertValue(_, OpenstackInfrastructureProvider.ATTRIBUTES) >> portAttributes

    and:
    result.cacheResults.get(PORTS.ns).first().id == portKey
    result.cacheResults.get(PORTS.ns).first().attributes == portAttributes
    noExceptionThrown()
  }

  void "test load data exception"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    OpenstackClientProvider provider = Mock(OpenstackClientProvider)
    Throwable throwable = new OpenstackProviderException(ActionResponse.actionFailed('test', 1))

    when:
    cachingAgent.loadData(providerCache)

    then:
    1 * credentials.provider >> provider
    1 * provider.listPorts(region) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException == throwable
  }
}
