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
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.client.model.DisruptionBudget
import com.netflix.spinnaker.clouddriver.titus.client.model.JobDisruptionBudgetUpdateRequest
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.RelocationLimit
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.SelfManaged
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.UnhealthyTasksLimit
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.UpsertJobDisruptionBudgetDescription
import spock.lang.Specification
import spock.lang.Subject

class UpsertDisruptionBudgetOperationSpec extends Specification {

  TitusClient titusClient = Mock(TitusClient)

  TitusClientProvider titusClientProvider = Stub(TitusClientProvider) {
    getTitusClient(_, _) >> titusClient
  }

  NetflixTitusCredentials testCredentials = new NetflixTitusCredentials(
      'test', 'test', 'test', [new TitusRegion('us-east-1', 'test', 'http://foo', false, false, "blah", "blah", 7104, [])], 'test', 'test', 'test', 'test', false, '', 'mainvpc', [], "", false, false, false
  )

  DisruptionBudget disruptionBudget = new DisruptionBudget(
      selfManaged: new SelfManaged(relocationTimeMs: 1),
      rateUnlimited: false,
      relocationLimit: new RelocationLimit(limit: 1),
      unhealthyTasksLimit: new UnhealthyTasksLimit(limitOfUnhealthyContainers: 5)
  )

  UpsertJobDisruptionBudgetDescription description = new UpsertJobDisruptionBudgetDescription(
      jobId: "abc123", region: "us-east-1", credentials: testCredentials, disruptionBudget: disruptionBudget
  )

  @Subject
  AtomicOperation atomicOperation = new UpsertTitusJobDisruptionBudgetAtomicOperation(titusClientProvider, description)

  def setup() {
    Task task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
  }

  void 'UpdateDisruptionBudgetOperation should update Titus job successfully'() {

    when:
    atomicOperation.operate([])

    then:
    1 * titusClient.updateDisruptionBudget(new JobDisruptionBudgetUpdateRequest()
        .withDisruptionBudget(disruptionBudget)
        .withJobId("abc123"))
    disruptionBudget.rateUnlimited == false
    disruptionBudget.unhealthyTasksLimit.limitOfUnhealthyContainers == 5

  }
}
