/*
 * Copyright 2015-2016 Pivotal Inc.
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
import com.netflix.spinnaker.clouddriver.cf.deploy.description.DestroyCloudFoundryServerGroupDescription
import com.netflix.spinnaker.clouddriver.cf.security.TestCloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskDisplayStatus
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import org.cloudfoundry.client.lib.CloudFoundryOperations
import org.cloudfoundry.client.lib.domain.CloudApplication
import org.springframework.web.client.ResourceAccessException
import spock.lang.Specification

class DestroyCloudFoundryServerGroupAtomicOperationSpec extends Specification {

  Task task

  CloudFoundryOperations client

  CloudFoundryOperations clientForNonExistentServerGroup

  def setup() {
    task = new DefaultTask('test')
    TaskRepository.threadLocalTask.set(task)

    client = Mock(CloudFoundryOperations)

    clientForNonExistentServerGroup = Mock(CloudFoundryOperations)
  }

  void "should not fail delete when server group does not exist"() {
    given:
    1 * clientForNonExistentServerGroup.deleteApplication(_) >> { throw new ResourceAccessException("app doesn't exist") }
    0 * clientForNonExistentServerGroup._

    def op = new DestroyCloudFoundryServerGroupAtomicOperation(
        new DestroyCloudFoundryServerGroupDescription(
            serverGroupName: "my-stack-v000",
            region: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: clientForNonExistentServerGroup)

    when:
    op.operate([])

    then:
    notThrown(Exception)
  }

  void "should delete server group"() {
    setup:
    def op = new DestroyCloudFoundryServerGroupAtomicOperation(
        new DestroyCloudFoundryServerGroupDescription(
            serverGroupName: "my-stack-v000",
            region: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)
    op.operationPoller = new OperationPoller(100, 100)

    when:
    op.operate([])

    then:
    1 * client.deleteApplication("my-stack-v000")
    1 * client.getApplications() >> { [new CloudApplication(null, 'something-else-v000')] }
    0 * client._

    task.history == [
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'INIT', status:'Creating task test', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DESTROY_SERVER_GROUP', status:'Initializing destruction of server group my-stack-v000 in staging...', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DESTROY_SERVER_GROUP', status:'Done operating on my-stack-v000.', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DESTROY_SERVER_GROUP', status:'Done destroying server group my-stack-v000 in staging.', state:'STARTED')),
    ]
  }

  void "platform failure causes timeout"() {
    setup:
    def op = new DestroyCloudFoundryServerGroupAtomicOperation(
        new DestroyCloudFoundryServerGroupDescription(
            serverGroupName: "my-stack-v000",
            region: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)
    op.operationPoller = new OperationPoller(1, 3)

    when:
    op.operate([])

    then:
    1 * client.deleteApplication("my-stack-v000")
    2 * client.getApplications() >> { [new CloudApplication(null, op.description.serverGroupName)] }
    0 * client._

    task.history == [
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'INIT', status:'Creating task test', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DESTROY_SERVER_GROUP', status:'Initializing destruction of server group my-stack-v000 in staging...', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DESTROY_SERVER_GROUP', status:'Operation on my-stack-v000 timed out.', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'DESTROY_SERVER_GROUP', status:'Failed to delete server group my-stack-v000 => Operation on my-stack-v000 timed out.', state:'STARTED')),

    ]
  }

}
