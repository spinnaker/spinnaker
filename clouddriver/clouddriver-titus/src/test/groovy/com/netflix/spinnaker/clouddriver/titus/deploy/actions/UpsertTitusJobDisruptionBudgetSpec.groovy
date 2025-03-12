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
package com.netflix.spinnaker.clouddriver.titus.deploy.actions

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.client.model.DisruptionBudget
import com.netflix.spinnaker.clouddriver.titus.client.model.JobDisruptionBudgetUpdateRequest
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.RelocationLimit
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.SelfManaged
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.UnhealthyTasksLimit
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.ServiceJobProcessesRequest
import com.netflix.spinnaker.clouddriver.titus.deploy.description.UpsertJobDisruptionBudgetDescription
import com.netflix.spinnaker.clouddriver.titus.deploy.ops.UpsertTitusJobDisruptionBudgetAtomicOperation
import com.netflix.spinnaker.fiat.model.resources.Permissions
import spock.lang.Specification
import spock.lang.Subject

class UpsertTitusJobDisruptionBudgetSpec extends Specification {
  def testCredentials = Mock(NetflixTitusCredentials)

  def accountCredentialsProvider = Mock(AccountCredentialsProvider)
  def titusClientProvider = Mock(TitusClientProvider)
  def titusClient = Mock(TitusClient)

  @Subject
  UpsertTitusJobDisruptionBudget upsertTitusJobDisruptionBudget = new UpsertTitusJobDisruptionBudget(
    accountCredentialsProvider, titusClientProvider
  )

  void 'should update disruption budget'() {
    given:
    def saga = new Saga("my-saga", "my-id")
    def disruptionBudget = new DisruptionBudget()
    def description = new UpsertJobDisruptionBudgetDescription(
      jobId: "my-job-id", region: "us-east-1", credentials: testCredentials, disruptionBudget: disruptionBudget
    )
    def command = UpsertTitusJobDisruptionBudget.UpsertTitusJobDisruptionBudgetCommand.builder().description(
      description
    ).build()

    when:
    upsertTitusJobDisruptionBudget.apply(command, saga)

    then:
    1 * accountCredentialsProvider.getCredentials(_) >> { return testCredentials }
    1 * titusClientProvider.getTitusClient(testCredentials, "us-east-1") >> { return titusClient }

    1 * titusClient.updateDisruptionBudget(new JobDisruptionBudgetUpdateRequest()
        .withDisruptionBudget(disruptionBudget)
        .withJobId("my-job-id"))
    0 * _
  }
}
