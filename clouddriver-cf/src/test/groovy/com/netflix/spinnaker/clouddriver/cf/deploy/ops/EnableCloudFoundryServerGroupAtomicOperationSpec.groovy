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

import com.netflix.spinnaker.clouddriver.cf.TestCredential
import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConstants
import com.netflix.spinnaker.clouddriver.cf.deploy.description.EnableDisableCloudFoundryServerGroupDescription
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryLoadBalancer
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryResourceRetriever
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryServerGroup
import com.netflix.spinnaker.clouddriver.cf.security.TestCloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskDisplayStatus
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import org.cloudfoundry.client.lib.CloudFoundryException
import org.cloudfoundry.client.lib.CloudFoundryOperations
import org.cloudfoundry.client.lib.domain.CloudApplication
import org.cloudfoundry.client.lib.domain.CloudDomain
import org.cloudfoundry.client.lib.domain.CloudRoute
import org.cloudfoundry.client.lib.domain.InstanceInfo
import org.cloudfoundry.client.lib.domain.InstanceState
import org.cloudfoundry.client.lib.domain.InstancesInfo
import org.springframework.http.HttpStatus
import spock.lang.Specification

class EnableCloudFoundryServerGroupAtomicOperationSpec extends Specification {

  Task task

  CloudFoundryOperations client

  def setup() {
    task = new DefaultTask('test')
    TaskRepository.threadLocalTask.set(task)

    client = Mock(CloudFoundryOperations)
  }

  void "should bubble up exception if server group doesn't exist"() {
    given:
    def cloudFoundryResourceRetriever = Mock(CloudFoundryResourceRetriever)
    def serverGroupName = "my-stack-v000"
    def op = new EnableCloudFoundryServerGroupAtomicOperation(
        new EnableDisableCloudFoundryServerGroupDescription(
            serverGroupName: serverGroupName,
            zone: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)
    op.cloudFoundryResourceRetriever = cloudFoundryResourceRetriever

    when:
    op.operate([])

    then:
    1 * cloudFoundryResourceRetriever.getServerGroupByAccountAndServerGroupName() >> {
      [baz: [(serverGroupName): new CloudFoundryServerGroup(nativeLoadBalancers: [
          new CloudFoundryLoadBalancer(name: 'production', nativeRoute: new CloudRoute(null, 'production', new CloudDomain(null, 'cfapps.io', null), 1)),
          new CloudFoundryLoadBalancer(name: 'staging', nativeRoute: new CloudRoute(null, 'staging', new CloudDomain(null, 'cfapps.io', null), 1))])]]
    }
    0 * cloudFoundryResourceRetriever._

    1 * client.getApplication(serverGroupName) >> { throw new CloudFoundryException(HttpStatus.NOT_FOUND, "Not Found", "Application not found") }
    0 * client._

    CloudFoundryException e = thrown()
    e.statusCode == HttpStatus.NOT_FOUND

    task.history == [
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'INIT', status:'Creating task test', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'ENABLE_SERVER_GROUP', status:'Initializing enable server group operation for my-stack-v000 in staging...', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'ENABLE_SERVER_GROUP', status:'Server group my-stack-v000 does not exist. Aborting enable operation.', state:'STARTED')),
    ]
  }

  void "should enable standard server group"() {
    setup:
    def cloudFoundryResourceRetriever = Mock(CloudFoundryResourceRetriever)
    def serverGroupName = "my-stack-v000"
    def op = new EnableCloudFoundryServerGroupAtomicOperation(
        new EnableDisableCloudFoundryServerGroupDescription(
            serverGroupName: serverGroupName,
            zone: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)
    op.cloudFoundryResourceRetriever = cloudFoundryResourceRetriever
    op.operationPoller = new OperationPoller(1,3)
    def instancesInfo = Mock(InstancesInfo)

    when:
    op.operate([])

    then:
    1 * cloudFoundryResourceRetriever.getServerGroupByAccountAndServerGroupName() >> {
      [baz: [(serverGroupName): new CloudFoundryServerGroup(nativeLoadBalancers: [
          new CloudFoundryLoadBalancer(name: 'production', nativeRoute: new CloudRoute(null, 'production', new CloudDomain(null, 'cfapps.io', null), 1)),
          new CloudFoundryLoadBalancer(name: 'staging', nativeRoute: new CloudRoute(null, 'staging', new CloudDomain(null, 'cfapps.io', null), 1))])]]
    }
    0 * cloudFoundryResourceRetriever._

    1 * instancesInfo.instances >> { [new InstanceInfo([index: '0', state: InstanceState.STARTING.toString()])] }
    1 * instancesInfo.instances >> { [new InstanceInfo([index: '0', state: InstanceState.RUNNING.toString()])] }
    0 * instancesInfo._

    1 * client.getApplication(serverGroupName) >> {
      def app = new CloudApplication(null, serverGroupName)
      app.env = [(CloudFoundryConstants.LOAD_BALANCERS): 'production,staging']
      app.uris = ['other.cfapps.io']
      app
    }
    1 * client.updateApplicationUris(serverGroupName, ['other.cfapps.io', 'production.cfapps.io', 'staging.cfapps.io'])
    1 * client.startApplication(serverGroupName)
    2 * client.getApplicationInstances(serverGroupName) >> { instancesInfo }
    0 * client._

    task.history == [
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'INIT', status:'Creating task test', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'ENABLE_SERVER_GROUP', status:'Initializing enable server group operation for my-stack-v000 in staging...', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'ENABLE_SERVER_GROUP', status:'Registering instances with load balancers...', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'ENABLE_SERVER_GROUP', status:'Starting server group my-stack-v000', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'ENABLE_SERVER_GROUP', status:'Done operating on my-stack-v000.', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'ENABLE_SERVER_GROUP', status:'Done enabling server group my-stack-v000 in staging.', state:'STARTED')),
    ]
  }

  void "cannot enable server group with empty list of load balancers"() {
    setup:
    def cloudFoundryResourceRetriever = Mock(CloudFoundryResourceRetriever)
    def serverGroupName = "my-stack-v000"
    def op = new EnableCloudFoundryServerGroupAtomicOperation(
        new EnableDisableCloudFoundryServerGroupDescription(
            serverGroupName: serverGroupName,
            zone: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)
    op.cloudFoundryResourceRetriever = cloudFoundryResourceRetriever
    op.operationPoller = new OperationPoller(1,3)

    when:
    op.operate([])

    then:
    1 * cloudFoundryResourceRetriever.getServerGroupByAccountAndServerGroupName() >> {
      [baz: [(serverGroupName): new CloudFoundryServerGroup(nativeLoadBalancers: [
          new CloudFoundryLoadBalancer(name: 'production', nativeRoute: new CloudRoute(null, 'production', new CloudDomain(null, 'cfapps.io', null), 1)),
          new CloudFoundryLoadBalancer(name: 'staging', nativeRoute: new CloudRoute(null, 'staging', new CloudDomain(null, 'cfapps.io', null), 1))])]]
    }
    0 * cloudFoundryResourceRetriever._

    1 * client.getApplication(serverGroupName) >> {
      def app = new CloudApplication(null, serverGroupName)
      app.env = [(CloudFoundryConstants.LOAD_BALANCERS): '']
      app
    }
    0 * client._

    RuntimeException e = thrown()
    e.message == "${serverGroupName} is not linked to any load balancers and can NOT be enabled"

    task.history == [
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'INIT', status:'Creating task test', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'ENABLE_SERVER_GROUP', status:'Initializing enable server group operation for my-stack-v000 in staging...', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'ENABLE_SERVER_GROUP', status:'my-stack-v000 is not linked to any load balancers and can NOT be enabled', state:'STARTED')),
    ]
  }

  void "cannot enable server group with no list of load balancers"() {
    setup:
    def cloudFoundryResourceRetriever = Mock(CloudFoundryResourceRetriever)
    def serverGroupName = "my-stack-v000"
    def op = new EnableCloudFoundryServerGroupAtomicOperation(
        new EnableDisableCloudFoundryServerGroupDescription(
            serverGroupName: serverGroupName,
            zone: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)
    op.cloudFoundryResourceRetriever = cloudFoundryResourceRetriever
    op.operationPoller = new OperationPoller(1,3)

    when:
    op.operate([])

    then:
    1 * cloudFoundryResourceRetriever.getServerGroupByAccountAndServerGroupName() >> {
      [baz: [(serverGroupName): new CloudFoundryServerGroup(nativeLoadBalancers: [
          new CloudFoundryLoadBalancer(name: 'production', nativeRoute: new CloudRoute(null, 'production', new CloudDomain(null, 'cfapps.io', null), 1)),
          new CloudFoundryLoadBalancer(name: 'staging', nativeRoute: new CloudRoute(null, 'staging', new CloudDomain(null, 'cfapps.io', null), 1))])]]
    }
    0 * cloudFoundryResourceRetriever._

    1 * client.getApplication(serverGroupName) >> { new CloudApplication(null, serverGroupName) }
    0 * client._

    RuntimeException e = thrown()
    e.message == "${serverGroupName} is not linked to any load balancers and can NOT be enabled"

    task.history == [
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'INIT', status:'Creating task test', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'ENABLE_SERVER_GROUP', status:'Initializing enable server group operation for my-stack-v000 in staging...', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'ENABLE_SERVER_GROUP', status:'my-stack-v000 is not linked to any load balancers and can NOT be enabled', state:'STARTED')),
    ]
  }

}
