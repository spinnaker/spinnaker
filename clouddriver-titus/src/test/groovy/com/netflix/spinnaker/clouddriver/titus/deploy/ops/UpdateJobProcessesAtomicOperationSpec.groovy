/*
 * Copyright 2019 Netflix, Inc.
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
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.ServiceJobProcesses
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.ServiceJobProcessesRequest
import spock.lang.Specification
import spock.lang.Subject

class UpdateJobProcessesAtomicOperationSpec extends Specification {

  TitusClient titusClient = Mock(TitusClient)
  TitusClientProvider titusClientProvider = Stub()
  NetflixTitusCredentials testCredentials = Stub()
  ServiceJobProcesses serviceJobProcesses = new ServiceJobProcesses(
      disableIncreaseDesired : false,
      disableDecreaseDesired : true
  )

  ServiceJobProcessesRequest request = new ServiceJobProcessesRequest(
      jobId: "abc123", region: "us-east-1", credentials: testCredentials, serviceJobProcesses: serviceJobProcesses
  )

  @Subject
  AtomicOperation atomicOperation = new UpdateTitusJobProcessesAtomicOperation(titusClientProvider, request)

  def setup() {
    Task task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
    titusClientProvider.getTitusClient(_, _) >> titusClient
  }

  void 'UpdateJobProcessesOperation should update Titus job successfully'() {

    when:
    atomicOperation.operate([])

    then:
    1 * titusClient.updateScalingProcesses(request)
    request.serviceJobProcesses.disableIncreaseDesired == false
    request.serviceJobProcesses.disableDecreaseDesired == true

  }
}
