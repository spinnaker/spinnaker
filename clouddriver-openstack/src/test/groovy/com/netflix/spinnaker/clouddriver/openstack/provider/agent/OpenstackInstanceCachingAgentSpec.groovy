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

import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.compute.Server
import spock.lang.Specification

class OpenstackInstanceCachingAgentSpec extends Specification {

  OpenstackInstanceCachingAgent cachingAgent
  OpenstackNamedAccountCredentials namedAccountCredentials
  String region = 'east'

  void "setup"() {
    namedAccountCredentials = GroovyMock(OpenstackNamedAccountCredentials)
    cachingAgent = new OpenstackInstanceCachingAgent(namedAccountCredentials, region)
  }

  void "test load data"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    OpenstackCredentials credentials = GroovyMock()
    OpenstackClientProvider provider = Mock()
    Server server = Mock(Server)
    String name = 'test-server'

    when:
    CacheResult result = cachingAgent.loadData(providerCache)

    then:
    1 * namedAccountCredentials.credentials >> credentials
    1 * credentials.provider >> provider
    1 * provider.getInstances(region) >> [server]
    1 * server.name >> name

    and:
    result.cacheResults != null
    result.cacheResults.get(Keys.Namespace.INSTANCES.ns) != null
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
    1 * provider.getInstances(region) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException == throwable
  }
}
