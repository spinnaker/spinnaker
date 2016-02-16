/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.ops
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DestroyTitusServerGroupDescription
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import spock.lang.Specification
import spock.lang.Subject

class DestroyTitusServerGroupAtomicOperationSpec extends Specification {

  TitusClient titusClient = Mock(TitusClient)

  TitusClientProvider titusClientProvider = Stub(TitusClientProvider) {
    getTitusClient(_, _) >> titusClient
  }

  NetflixTitusCredentials testCredentials = new NetflixTitusCredentials(
    'test', 'test', 'test', [new TitusRegion('us-east-1', 'test', 'http://foo')]
  )

  DestroyTitusServerGroupDescription description = new DestroyTitusServerGroupDescription(
    serverGroupName: 'api-test-v000', region: 'us-east-1', credentials: testCredentials
  )

  @Subject
  AtomicOperation atomicOperation = new DestroyTitusServerGroupAtomicOperation(titusClientProvider, description)

  def setup() {
    Task task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
  }

  void 'DestroyTitusServerGroupAtomicOperation should terminate the Titus job successfully'() {
    given:
    titusClient.findJobByName('api-test-v000') >> { new Job(id: '1234') }

    when:
    atomicOperation.operate([])

    then:
    titusClient.terminateJob('1234')
  }
}
