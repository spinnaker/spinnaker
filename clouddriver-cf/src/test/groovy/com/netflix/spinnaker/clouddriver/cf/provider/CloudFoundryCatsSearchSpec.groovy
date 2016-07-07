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

package com.netflix.spinnaker.clouddriver.cf.provider
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory
import com.netflix.spinnaker.cats.provider.DefaultProviderRegistry
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import com.netflix.spinnaker.clouddriver.cache.CatsSearchProvider
import com.netflix.spinnaker.clouddriver.cf.TestCredential
import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConstants
import com.netflix.spinnaker.clouddriver.cf.provider.agent.ClusterCachingAgent
import com.netflix.spinnaker.clouddriver.cf.security.TestCloudFoundryClientFactory
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.domain.*
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils.buildNativeApplication
import static com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils.mapToMeta

class CloudFoundryCatsSearchSpec extends Specification {

  CloudFoundryClient client

  ClusterCachingAgent cachingAgent

  CatsSearchProvider searchProvider

  ProviderRegistry registry

  // Generated via https://www.uuidgenerator.net/version4
  final String uuid1 = '35807c3d-d71b-486a-a7c7-0d351b62dace'
  final String uuid2 = 'e6d70139-5415-48b3-adf3-a35471f70ab5'
  final String uuid3 = '78d845c9-900e-4144-be09-63d4f433a2fd'

  def setup() {
    client = Mock(CloudFoundryClient)
    cachingAgent = new ClusterCachingAgent(
        new TestCloudFoundryClientFactory(stubClient: client),
        TestCredential.named('test'),
        new ObjectMapper(),
        new DefaultRegistry()
    )
    def cloudFoundryProvider = new CloudFoundryProvider([cachingAgent])
    registry = new DefaultProviderRegistry([cloudFoundryProvider],
        new InMemoryNamedCacheFactory())

    searchProvider = new CatsSearchProvider(registry.getProviderCache(CloudFoundryProvider.PROVIDER_NAME), [cloudFoundryProvider])
  }

  def "should handle basic search"() {
    when:
    cachingAgent.getAgentExecution(registry).executeAgent(cachingAgent)

    def searchResultSet = searchProvider.search('testapp', cachingAgent.providedDataTypes.collect {it.typeName}, 1, 100, [:])

    then:
    searchResultSet.totalMatches == 2
    searchResultSet.results.size() == 2
    searchResultSet.results[0].provider == 'cf'
    searchResultSet.results[0].type == 'clusters'
    searchResultSet.results[0].application == 'testapp'
    searchResultSet.results[0].account == 'test'
    searchResultSet.results[0].cluster == 'testapp-production'
    searchResultSet.results[0].stack == 'production'
    searchResultSet.results[0].detail == null
    searchResultSet.results[0].url == '/applications/testapp/clusters/test/testapp-production'

    searchResultSet.results[1].provider == 'cf'
    searchResultSet.results[1].type == 'serverGroups'
    searchResultSet.results[1].application == 'testapp'
    searchResultSet.results[1].cluster == 'testapp-production'
    searchResultSet.results[1].account == 'test'
    searchResultSet.results[1].region == 'spinnaker'
    searchResultSet.results[1].serverGroup == 'testapp-production-v001'
    searchResultSet.results[1].stack == 'production'
    searchResultSet.results[1].detail == null
    searchResultSet.results[1].sequence == '1'
    searchResultSet.results[1].url == '/applications/testapp/clusters/test/testapp-production/cf/serverGroups/testapp-production-v001?region=spinnaker'

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

}
