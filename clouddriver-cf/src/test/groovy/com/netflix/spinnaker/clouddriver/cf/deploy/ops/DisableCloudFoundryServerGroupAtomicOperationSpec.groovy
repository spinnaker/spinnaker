/*
 * Copyright 2016 Pivotal Inc.
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

package com.netflix.spinnaker.clouddriver.cf.deploy.ops
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory
import com.netflix.spinnaker.cats.provider.DefaultProviderRegistry
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import com.netflix.spinnaker.clouddriver.cf.TestCredential
import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConstants
import com.netflix.spinnaker.clouddriver.cf.deploy.description.EnableDisableCloudFoundryServerGroupDescription
import com.netflix.spinnaker.clouddriver.cf.provider.CloudFoundryProvider
import com.netflix.spinnaker.clouddriver.cf.provider.agent.ClusterCachingAgent
import com.netflix.spinnaker.clouddriver.cf.provider.view.CloudFoundryClusterProvider
import com.netflix.spinnaker.clouddriver.cf.security.TestCloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.data.task.*
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.CloudFoundryException
import org.cloudfoundry.client.lib.CloudFoundryOperations
import org.cloudfoundry.client.lib.domain.*
import org.springframework.http.HttpStatus
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils.buildNativeApplication
import static com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils.mapToMeta

class DisableCloudFoundryServerGroupAtomicOperationSpec extends Specification {

  Task task

  CloudFoundryOperations client

  ClusterCachingAgent cachingAgent

  ProviderRegistry registry

  CloudFoundryClusterProvider clusterProvider

  // Generated via https://www.uuidgenerator.net/version4
  final String uuid1 = '35807c3d-d71b-486a-a7c7-0d351b62dace'
  final String uuid2 = 'e6d70139-5415-48b3-adf3-a35471f70ab5'
  final String uuid3 = '78d845c9-900e-4144-be09-63d4f433a2fd'

  def setup() {
    task = new DefaultTask('test')
    TaskRepository.threadLocalTask.set(task)

    client = Mock(CloudFoundryClient)
    cachingAgent = new ClusterCachingAgent(
        new TestCloudFoundryClientFactory(stubClient: client),
        TestCredential.named('baz'),
        new ObjectMapper(),
        new DefaultRegistry()
    )

    def cloudFoundryProvider = new CloudFoundryProvider([cachingAgent])
    registry = new DefaultProviderRegistry([cloudFoundryProvider], new InMemoryNamedCacheFactory())
    clusterProvider = new CloudFoundryClusterProvider(registry.getProviderCache(CloudFoundryProvider.PROVIDER_NAME), cloudFoundryProvider, new ObjectMapper())
  }

  void "should bubble up exception if server group doesn't exist"() {
    given:
    def serverGroupName = "my-stack-v000"
    def op = new DisableCloudFoundryServerGroupAtomicOperation(
        new EnableDisableCloudFoundryServerGroupDescription(
            serverGroupName: serverGroupName,
            region: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)
    op.clusterProvider = clusterProvider

    when:
    cachingAgent.getAgentExecution(registry).executeAgent(cachingAgent)

    op.operate([])

    then:
    CloudFoundryException e = thrown()
    e.statusCode == HttpStatus.NOT_FOUND

    task.history == [
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'INIT', status:'Creating task test', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DISABLE_SERVER_GROUP', status:'Initializing disable server group operation for my-stack-v000 in staging...', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DISABLE_SERVER_GROUP', status:'Server group my-stack-v000 does not exist. Aborting disable operation.', state:'STARTED')),
    ]

    1 * client.getApplication(serverGroupName) >> { throw new CloudFoundryException(HttpStatus.NOT_FOUND, "Not Found", "Application not found") }

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

  void "should disable standard server group"() {
    setup:
    def serverGroupName = "my-stack-v000"
    def op = new DisableCloudFoundryServerGroupAtomicOperation(
        new EnableDisableCloudFoundryServerGroupDescription(
            serverGroupName: serverGroupName,
            region: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)
    op.clusterProvider = clusterProvider
    op.operationPoller = new OperationPoller(1,3)

    when:
    cachingAgent.getAgentExecution(registry).executeAgent(cachingAgent)

    op.operate([])

    then:
    1 * client.getApplication(serverGroupName) >> {
      def app = new CloudApplication(null, serverGroupName)
      app.env = [(CloudFoundryConstants.LOAD_BALANCERS): 'production,staging']
      app.uris = ['other.cfapps.io', 'production.cfapps.io', 'staging.cfapps.io']
      app
    }
    1 * client.getApplication(serverGroupName) >> {
      def app = new CloudApplication(null, serverGroupName)
      app.state = CloudApplication.AppState.STARTED
      app
    }
    1 * client.getApplication(serverGroupName) >> {
      def app = new CloudApplication(null, serverGroupName)
      app.state = CloudApplication.AppState.STOPPED
      app
    }
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'cfapps.io', null) }
    1 * client.updateApplicationUris(serverGroupName, ['other.cfapps.io'])
    1 * client.stopApplication(serverGroupName)
    1 * client.getApplicationInstances(_) >> { new InstancesInfo([]) }

    task.history == [
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'INIT', status:'Creating task test', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DISABLE_SERVER_GROUP', status:'Initializing disable server group operation for my-stack-v000 in staging...', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DISABLE_SERVER_GROUP', status:'Deregistering instances from load balancers...', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DISABLE_SERVER_GROUP', status:'Stopping server group my-stack-v000', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DISABLE_SERVER_GROUP', status:'Done operating on my-stack-v000.', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DISABLE_SERVER_GROUP', status:'Done disabling server group my-stack-v000 in staging.', state:'STARTED')),
    ]
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
    1 * client.getRoutes('cfapps.io') >> { [
        new CloudRoute(null, 'other', new CloudDomain(null, 'cfapps.io', null), 1),
        new CloudRoute(null, 'production', new CloudDomain(null, 'cfapps.io', null), 1),
        new CloudRoute(null, 'staging', new CloudDomain(null, 'cfapps.io', null), 1)
    ]}
    1 * client.applications >> {
      [
          buildNativeApplication([
              name     : serverGroupName,
              state    : CloudApplication.AppState.STARTED.toString(),
              instances: 1,
              services : ['spinnaker-redis'],
              memory   : 1024,
              env      : ["${CloudFoundryConstants.LOAD_BALANCERS}=production,staging".toString()],
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

    0 * client._
  }

  void "cannot disable server group with empty list of load balancers"() {
    setup:
    def serverGroupName = "my-stack-v000"
    def op = new DisableCloudFoundryServerGroupAtomicOperation(
        new EnableDisableCloudFoundryServerGroupDescription(
            serverGroupName: serverGroupName,
            region: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)
    op.clusterProvider = clusterProvider
    op.operationPoller = new OperationPoller(1,3)

    when:
    cachingAgent.getAgentExecution(registry).executeAgent(cachingAgent)

    op.operate([])

    then:
    1 * client.getApplication(serverGroupName) >> {
      def app = new CloudApplication(null, serverGroupName)
      app.env = [(CloudFoundryConstants.LOAD_BALANCERS): '']
      app
    }
    1 * client.getApplicationInstances(_) >> { new InstancesInfo([])}

    RuntimeException e = thrown()
    e.message == "${serverGroupName} is not linked to any load balancers and can NOT be disabled"

    task.history == [
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'INIT', status:'Creating task test', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DISABLE_SERVER_GROUP', status:'Initializing disable server group operation for my-stack-v000 in staging...', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DISABLE_SERVER_GROUP', status:'my-stack-v000 is not linked to any load balancers and can NOT be disabled', state:'STARTED')),
    ]

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
    1 * client.getRoutes('cfapps.io') >> { [
        new CloudRoute(null, 'other', new CloudDomain(null, 'cfapps.io', null), 1),
        new CloudRoute(null, 'production', new CloudDomain(null, 'cfapps.io', null), 1),
        new CloudRoute(null, 'staging', new CloudDomain(null, 'cfapps.io', null), 1)
    ]}
    1 * client.applications >> {
      [
          buildNativeApplication([
              name     : serverGroupName,
              state    : CloudApplication.AppState.STARTED.toString(),
              instances: 1,
              services : ['spinnaker-redis'],
              memory   : 1024,
              env      : ["${CloudFoundryConstants.LOAD_BALANCERS}=production,staging".toString()],
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

    0 * client._
  }

  void "cannot disable server group with no list of load balancers"() {
    setup:
    def serverGroupName = "my-stack-v000"
    def op = new DisableCloudFoundryServerGroupAtomicOperation(
        new EnableDisableCloudFoundryServerGroupDescription(
            serverGroupName: serverGroupName,
            region: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)
    op.clusterProvider = clusterProvider
    op.operationPoller = new OperationPoller(1,3)

    when:
    cachingAgent.getAgentExecution(registry).executeAgent(cachingAgent)

    op.operate([])

    then:
    1 * client.getApplication(serverGroupName) >> { new CloudApplication(null, serverGroupName) }
    1 * client.getApplicationInstances(_) >> { new InstancesInfo([])}

    RuntimeException e = thrown()
    e.message == "${serverGroupName} is not linked to any load balancers and can NOT be disabled"

    task.history == [
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'INIT', status:'Creating task test', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DISABLE_SERVER_GROUP', status:'Initializing disable server group operation for my-stack-v000 in staging...', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DISABLE_SERVER_GROUP', status:'my-stack-v000 is not linked to any load balancers and can NOT be disabled', state:'STARTED')),
    ]

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
    1 * client.getRoutes('cfapps.io') >> { [
        new CloudRoute(null, 'other', new CloudDomain(null, 'cfapps.io', null), 1),
        new CloudRoute(null, 'production', new CloudDomain(null, 'cfapps.io', null), 1),
        new CloudRoute(null, 'staging', new CloudDomain(null, 'cfapps.io', null), 1)
    ]}
    1 * client.applications >> {
      [
          buildNativeApplication([
              name     : serverGroupName,
              state    : CloudApplication.AppState.STARTED.toString(),
              instances: 1,
              services : ['spinnaker-redis'],
              memory   : 1024,
              env      : ["${CloudFoundryConstants.LOAD_BALANCERS}=production,staging".toString()],
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

    0 * client._
  }

}
