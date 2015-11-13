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

package com.netflix.spinnaker.kato.titan.deploy.ops
import com.netflix.spinnaker.clouddriver.titan.TitanClientProvider
import com.netflix.spinnaker.clouddriver.titan.credentials.NetflixTitanCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.titan.deploy.description.DestroyTitanServerGroupDescription
import com.netflix.titanclient.TitanClient
import com.netflix.titanclient.TitanRegion
import com.netflix.titanclient.model.Job
import spock.lang.Specification
import spock.lang.Subject

class DestroyTitanServerGroupAtomicOperationSpec extends Specification {

  TitanClient titanClient = Mock(TitanClient)

  TitanClientProvider titanClientProvider = Stub(TitanClientProvider) {
    getTitanClient(_, _) >> titanClient
  }

  NetflixTitanCredentials testCredentials = new NetflixTitanCredentials(
    'test', 'test', 'test', [new TitanRegion('us-east-1', 'test', 'http://foo', 'http://bar')]
  )

  DestroyTitanServerGroupDescription description = new DestroyTitanServerGroupDescription(
    serverGroupName: 'api-test-v000', region: 'us-east-1', credentials: testCredentials
  )

  @Subject
  AtomicOperation atomicOperation = new DestroyTitanServerGroupAtomicOperation(titanClientProvider, description)

  def setup() {
    Task task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
  }

  void 'DestroyTitanServerGroupAtomicOperation should terminate the Titan job successfully'() {
    given:
    titanClient.findJobByName('api-test-v000') >> { new Job(id: '1234') }

    when:
    atomicOperation.operate([])

    then:
    titanClient.terminateJob('1234')
  }
}
