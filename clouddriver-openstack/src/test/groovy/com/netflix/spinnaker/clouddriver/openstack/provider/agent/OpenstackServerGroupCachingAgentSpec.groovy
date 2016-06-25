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

import com.google.common.collect.Sets
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache

import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.heat.Stack
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS

class OpenstackServerGroupCachingAgentSpec extends Specification {

  OpenstackServerGroupCachingAgent cachingAgent
  OpenstackNamedAccountCredentials namedAccountCredentials
  String region = 'east'
  String account = 'test'

  void "setup"() {
    namedAccountCredentials = GroovyMock(OpenstackNamedAccountCredentials)
    cachingAgent = Spy(OpenstackServerGroupCachingAgent, constructorArgs: [namedAccountCredentials, region])
  }

  void "test load data"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    OpenstackCredentials credentials = GroovyMock()
    OpenstackClientProvider provider = Mock()
    Stack stack = Mock(Stack)
    String appName = 'testapp'
    String clusterName = "${appName}-stack-detail"
    String serverGroupName =  "${clusterName}-v000"

    and:
    String clusterKey = Keys.getClusterKey(account, appName, clusterName)
    String appKey = Keys.getApplicationKey(appName)
    String serverGroupKey = Keys.getServerGroupKey(serverGroupName, account, region)

    when:
    CacheResult result = cachingAgent.loadData(providerCache)

    then:
    1 * namedAccountCredentials.credentials >> credentials
    3 * cachingAgent.getAccountName() >> account
    1 * credentials.provider >> provider
    1 * provider.listStacks(region) >> [stack]
    1 * stack.name >> serverGroupName

    and:
    result.cacheResults != null
    noExceptionThrown()

    and:
    Collection<CacheData> applicationData = result.cacheResults.get(APPLICATIONS.ns)
    applicationData.size() == 1
    applicationData.first().with {
      id == appKey
      attributes == ['name':appName]
      relationships == [(CLUSTERS.ns) : Sets.newHashSet(clusterKey)]
    }

    and:
    Collection<CacheData> clusterData = result.cacheResults.get(CLUSTERS.ns)
    clusterData.size() == 1
    clusterData.first().with {
      id == clusterKey
      attributes == ['name':clusterName, 'accountName': account]
      relationships == [(APPLICATIONS.ns) : Sets.newHashSet(appKey), (SERVER_GROUPS.ns) : Sets.newHashSet(serverGroupKey)]
    }

    and:
    Collection<CacheData> serverGroupData = result.cacheResults.get(SERVER_GROUPS.ns)
    serverGroupData.size() == 1
    serverGroupData.first().with {
      id == serverGroupKey
      attributes == [:]
      relationships == [(APPLICATIONS.ns) : Sets.newHashSet(appKey), (CLUSTERS.ns) : Sets.newHashSet(clusterKey)]
    }
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
    1 * provider.listStacks(region) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException == throwable
  }
}
