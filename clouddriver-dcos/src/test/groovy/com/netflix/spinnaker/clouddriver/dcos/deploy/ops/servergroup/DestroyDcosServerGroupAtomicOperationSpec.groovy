/*
 * Copyright 2018 Cerner Corporation
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

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DestroyDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.Result
import spock.lang.Subject

class DestroyDcosServerGroupAtomicOperationSpec extends BaseSpecification {
  private static final APPLICATION_NAME = 'api-test-v000'

  DCOS dcosClient = Mock(DCOS)

  DcosAccountCredentials testCredentials = defaultCredentialsBuilder().build()

  DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
    getDcosClient(testCredentials, DEFAULT_REGION) >> dcosClient
  }

  DestroyDcosServerGroupDescription description = new DestroyDcosServerGroupDescription(
    serverGroupName: APPLICATION_NAME, credentials: testCredentials, dcosCluster: DEFAULT_REGION, region: DEFAULT_REGION
  )

  @Subject
  AtomicOperation atomicOperation = new DestroyDcosServerGroupAtomicOperation(dcosClientProvider, description)

  def setup() {
    Task task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
  }

  void 'DestroyDcosServerGroupAtomicOperation should terminate the DCOS service successfully'() {
    when:
    atomicOperation.operate([])

    then:
    noExceptionThrown()
    1 * dcosClient.deleteApp(_, _) >> new Result()
  }
}
