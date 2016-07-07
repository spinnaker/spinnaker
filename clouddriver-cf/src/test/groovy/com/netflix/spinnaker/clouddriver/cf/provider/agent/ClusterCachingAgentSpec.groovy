/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.provider.agent
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.cats.provider.DefaultProviderCache
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cf.CloudFoundryCloudProvider
import com.netflix.spinnaker.clouddriver.cf.TestCredential
import com.netflix.spinnaker.clouddriver.cf.cache.Keys
import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConstants
import com.netflix.spinnaker.clouddriver.cf.provider.CloudFoundryProvider
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.clouddriver.cf.security.TestCloudFoundryClientFactory
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.domain.*
import spock.lang.Specification

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.cf.cache.Keys.Namespace.*
import static com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils.buildNativeApplication
import static com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils.mapToMeta

class ClusterCachingAgentSpec extends Specification {

  CloudFoundryClient client

  CloudFoundryAccountCredentials account

  ClusterCachingAgent cachingAgent

  ProviderCache cache

  // Generated via https://www.uuidgenerator.net/version4
  final String uuid1 = '35807c3d-d71b-486a-a7c7-0d351b62dace'
  final String uuid2 = 'e6d70139-5415-48b3-adf3-a35471f70ab5'
  final String uuid3 = '78d845c9-900e-4144-be09-63d4f433a2fd'

  def setup() {
    client = Mock(CloudFoundryClient)

    account = TestCredential.named('test')
    cachingAgent = new ClusterCachingAgent(
        new TestCloudFoundryClientFactory(stubClient: client),
        this.account,
        new ObjectMapper(),
        new DefaultRegistry()
    )

    cache = new DefaultProviderCache(new InMemoryCache())
  }

  def "handles CF caching"() {
    expect:
    cachingAgent.handles(OnDemandAgent.OnDemandType.ServerGroup, 'cf')
  }

  def "should report standard info"() {
    expect:
    cachingAgent.providerName == CloudFoundryProvider.name
    cachingAgent.agentType == 'test/ClusterCachingAgent'
    cachingAgent.accountName == 'test'
    cachingAgent.onDemandAgentType == 'test/ClusterCachingAgent-OnDemand'
    cachingAgent.providedDataTypes.collect { it.authority }.contains(AUTHORITATIVE)
    cachingAgent.providedDataTypes.collect {
      it.typeName
    }.containsAll([SERVER_GROUPS.ns, CLUSTERS.ns, APPLICATIONS.ns, INSTANCES.ns, LOAD_BALANCERS.ns])
  }

  def "should handle simple caching"() {
    when:
    def results = cachingAgent.loadData(cache)

    then:
    results.cacheResults.serverGroups.size() == 1
    results.cacheResults.serverGroups[0].id == Keys.getServerGroupKey('testapp-production-v001', 'test', 'spinnaker')
    results.cacheResults.serverGroups[0].ttlSeconds == -1
    results.cacheResults.serverGroups[0].attributes.name == 'testapp-production-v001'
    results.cacheResults.serverGroups[0].attributes.logsLink == "http://console.cf.example.com/organizations/${uuid2}/spaces/${uuid1}/applications/${uuid2}/tailing_logs"
    results.cacheResults.serverGroups[0].attributes.consoleLink == "http://console.cf.example.com/organizations/${uuid2}/spaces/${uuid1}/applications/${uuid2}"
    results.cacheResults.serverGroups[0].attributes.services.size() == 1
    results.cacheResults.serverGroups[0].attributes.services[0].id == uuid3
    results.cacheResults.serverGroups[0].attributes.services[0].name == 'spinnaker-redis'
    results.cacheResults.serverGroups[0].attributes.services[0].application == 'testapp'
    results.cacheResults.serverGroups[0].attributes.services[0].accountName == 'test'
    results.cacheResults.serverGroups[0].attributes.services[0].region == 'spinnaker'
    results.cacheResults.serverGroups[0].attributes.services[0].nativeService.name == 'spinnaker-redis'
    results.cacheResults.serverGroups[0].relationships.applications.size() == 1
    results.cacheResults.serverGroups[0].relationships.applications.contains(Keys.getApplicationKey('testapp'))
    results.cacheResults.serverGroups[0].relationships.clusters.size() == 1
    results.cacheResults.serverGroups[0].relationships.clusters.contains(Keys.getClusterKey('testapp-production', 'testapp', 'test'))
    results.cacheResults.serverGroups[0].relationships.instances.size() == 1
    results.cacheResults.serverGroups[0].relationships.instances.contains(Keys.getInstanceKey('testapp-production-v001(0)', 'test', 'spinnaker'))
    results.cacheResults.serverGroups[0].relationships.loadBalancers.size() == 1
    results.cacheResults.serverGroups[0].relationships.loadBalancers[0].contains(Keys.getLoadBalancerKey('my-cool-test-app', 'test', 'spinnaker'))

    results.cacheResults.applications.size() == 1
    results.cacheResults.applications[0].id == Keys.getApplicationKey('testapp')
    results.cacheResults.applications[0].ttlSeconds == -1
    results.cacheResults.applications[0].attributes.name == 'testapp'
    results.cacheResults.applications[0].relationships.clusters.contains(Keys.getClusterKey('testapp-production', 'testapp', 'test'))
    results.cacheResults.applications[0].relationships.serverGroups.contains(Keys.getServerGroupKey('testapp-production-v001', 'test', 'spinnaker'))
    results.cacheResults.applications[0].relationships.loadBalancers.contains(Keys.getLoadBalancerKey('my-cool-test-app', 'test', 'spinnaker'))

    results.cacheResults.clusters.size() == 1
    results.cacheResults.clusters[0].id == Keys.getClusterKey('testapp-production', 'testapp', 'test')
    results.cacheResults.clusters[0].ttlSeconds == -1
    results.cacheResults.clusters[0].attributes.name == 'testapp-production'
    results.cacheResults.clusters[0].relationships.applications.contains(Keys.getApplicationKey('testapp'))
    results.cacheResults.clusters[0].relationships.serverGroups.contains(Keys.getServerGroupKey('testapp-production-v001', 'test', 'spinnaker'))
    results.cacheResults.clusters[0].relationships.loadBalancers.contains(Keys.getLoadBalancerKey('my-cool-test-app', 'test', 'spinnaker'))

    results.cacheResults.instances.size() == 1
    results.cacheResults.instances[0].id == Keys.getInstanceKey('testapp-production-v001(0)', 'test', 'spinnaker')
    results.cacheResults.instances[0].ttlSeconds == -1
    results.cacheResults.instances[0].attributes.name == 'testapp-production-v001(0)'
    results.cacheResults.instances[0].relationships.serverGroups.contains(Keys.getServerGroupKey('testapp-production-v001', 'test', 'spinnaker'))

    results.cacheResults.loadBalancers.size() == 1
    results.cacheResults.loadBalancers[0].id == Keys.getLoadBalancerKey('my-cool-test-app', 'test', 'spinnaker')
    results.cacheResults.loadBalancers[0].ttlSeconds == -1
    results.cacheResults.loadBalancers[0].attributes.name == 'my-cool-test-app'
    results.cacheResults.loadBalancers[0].attributes.nativeRoute.name == 'my-cool-test-app.cfapps.io'
    results.cacheResults.loadBalancers[0].relationships.serverGroups.contains(Keys.getServerGroupKey('testapp-production-v001', 'test', 'spinnaker'))
    results.cacheResults.loadBalancers[0].relationships.applications.contains(Keys.getApplicationKey('testapp'))
    results.cacheResults.loadBalancers[0].relationships.instances.size() == 1
    results.cacheResults.loadBalancers[0].relationships.instances.contains(Keys.getInstanceKey('testapp-production-v001(0)', 'test', 'spinnaker'))

    1 * client.spaces >> {
      [
          new CloudSpace(
              mapToMeta([guid: uuid1, created: 1L]),
              "test",
              new CloudOrganization(
                  mapToMeta([guid: uuid2, created: 2L]),
                  "spinnaker"))
      ]
    }
    1 * client.services >> { [new CloudService(mapToMeta([guid: uuid3, created: 3L]), 'spinnaker-redis')] }
    1 * client.domainsForOrg >> { [new CloudDomain(null, 'cfapps.io', null)] }
    1 * client.getRoutes('cfapps.io') >> {
      [new CloudRoute(null, 'my-cool-test-app', new CloudDomain(null, 'cfapps.io', null), 1)]
    }
    1 * client.applications >> {
      [
          buildNativeApplication([
              name     : 'testapp-production-v001',
              state    : CloudApplication.AppState.STARTED.toString(),
              instances: 1,
              services : ['spinnaker-redis'],
              memory   : 1024,
              env      : ["${CloudFoundryConstants.LOAD_BALANCERS}=my-cool-test-app".toString()],
              meta     : [
                  guid   : uuid2,
                  created: 5L
              ],
              space    : [
                  meta        : [
                      guid   : uuid3,
                      created: 6L
                  ],
                  name        : 'test',
                  organization: [
                      meta: [
                          guid   : uuid1,
                          created: 7L
                      ],
                      name: 'spinnaker'
                  ]
              ]
          ])
      ]
    }
    1 * client.getApplicationInstances(_) >> {
      new InstancesInfo([
          [since: 1L, index: 0, state: InstanceState.RUNNING.toString()]
      ])
    }

    0 * client._
  }

  def "should handle on-demand update to an existing server group"() {
    when:
    // Initial cache load while application is starting
    def initialResults = cachingAgent.loadData(cache)

    /**
     * Assume application has successfully started
     */

    def onDemandUpdate = cachingAgent.handle(cache, [serverGroupName: 'testapp-production-v001', account: 'test', region: 'spinnaker'])

    def finalResults = cachingAgent.loadData(cache)

    then:
    initialResults.cacheResults.instances.size() == 1
    initialResults.cacheResults.instances[0].id == Keys.getInstanceKey('testapp-production-v001(0)', 'test', 'spinnaker')
    initialResults.cacheResults.instances[0].ttlSeconds == -1
    initialResults.cacheResults.instances[0].attributes.name == 'testapp-production-v001(0)'
    initialResults.cacheResults.instances[0].attributes.nativeInstance.state == InstanceState.STARTING

    onDemandUpdate.cacheResult.cacheResults.instances.size() == 1
    onDemandUpdate.cacheResult.cacheResults.instances[0].id == Keys.getInstanceKey('testapp-production-v001(0)', 'test', 'spinnaker')
    onDemandUpdate.cacheResult.cacheResults.instances[0].ttlSeconds == -1
    onDemandUpdate.cacheResult.cacheResults.instances[0].attributes.name == 'testapp-production-v001(0)'
    onDemandUpdate.cacheResult.cacheResults.instances[0].attributes.nativeInstance.state == InstanceState.RUNNING

    finalResults.cacheResults.instances.size() == 1
    finalResults.cacheResults.instances[0].id == Keys.getInstanceKey('testapp-production-v001(0)', 'test', 'spinnaker')
    finalResults.cacheResults.instances[0].ttlSeconds == -1
    finalResults.cacheResults.instances[0].attributes.name == 'testapp-production-v001(0)'
    finalResults.cacheResults.instances[0].attributes.nativeInstance.state == InstanceState.RUNNING

    3 * client.spaces >> {
      [
          new CloudSpace(
              mapToMeta([guid: uuid1, created: 1L]),
              "test",
              new CloudOrganization(
                  mapToMeta([guid: uuid2, created: 2L]),
                  "spinnaker"))
      ]
    }
    3 * client.services >> { [new CloudService(mapToMeta([guid: uuid3, created: 3L]), 'spinnaker-redis')] }
    3 * client.domainsForOrg >> { [new CloudDomain(null, 'cfapps.io', null)] }
    3 * client.getRoutes('cfapps.io') >> {
      [new CloudRoute(null, 'my-cool-test-app', new CloudDomain(null, 'cfapps.io', null), 1)]
    }
    2 * client.applications >> {
      [
          buildNativeApplication([
              name     : 'testapp-production-v001',
              state    : CloudApplication.AppState.STARTED.toString(),
              instances: 1,
              services : ['spinnaker-redis'],
              memory   : 1024,
              env      : ["${CloudFoundryConstants.LOAD_BALANCERS}=my-cool-test-app".toString()],
              meta     : [
                  guid   : uuid2,
                  created: 5L
              ],
              space    : [
                  meta        : [
                      guid   : uuid3,
                      created: 6L
                  ],
                  name        : 'test',
                  organization: [
                      meta: [
                          guid   : uuid1,
                          created: 7L
                      ],
                      name: 'spinnaker'
                  ]
              ]
          ])
      ]
    }
    1 * client.getApplicationInstances(_) >> {
      new InstancesInfo([
          [since: 1L, index: 0, state: InstanceState.STARTING.toString()]
      ])
    }
    1 * client.getApplication(_) >> {
      buildNativeApplication([
          name     : 'testapp-production-v001',
          state    : CloudApplication.AppState.STARTED.toString(),
          instances: 1,
          services : ['spinnaker-redis'],
          memory   : 1024,
          env      : ["${CloudFoundryConstants.LOAD_BALANCERS}=my-cool-test-app".toString()],
          meta     : [
              guid   : uuid2,
              created: 5L
          ],
          space    : [
              meta        : [
                  guid   : uuid3,
                  created: 6L
              ],
              name        : 'test',
              organization: [
                  meta: [
                      guid   : uuid1,
                      created: 7L
                  ],
                  name: 'spinnaker'
              ]
          ]
      ])
    }
    2 * client.getApplicationInstances(_) >> {
      new InstancesInfo([
          [since: 2L, index: 0, state: InstanceState.RUNNING.toString()]
      ])
    }

    0 * client._
  }
}
