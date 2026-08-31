/*
 * Copyright 2026 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.instance.ops

import com.netflix.spinnaker.clouddriver.azure.client.AzureComputeClient
import com.netflix.spinnaker.clouddriver.azure.resources.instance.model.AzureInstanceDescription
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification

class TerminateAzureInstanceAndDecrementServerGroupAtomicOperationSpec extends Specification {
  private static final SERVER_GROUP = "myapp-dev-v086"
  private static final REGION = "westus"

  AzureComputeClient computeClient = Mock(AzureComputeClient)
  // AzureCredentials.computeClient is a final field, so its getter is final and CGLIB-based
  // Mock() cannot stub it; GroovyMock intercepts via the metaclass.
  AzureCredentials credentials = GroovyMock(AzureCredentials) { getComputeClient() >> computeClient }

  def setup() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "accepts the singular 'instance' field orca sends and deletes it"() {
    given:
    def description = new AzureInstanceDescription()
    description.instance = "${SERVER_GROUP}_3"
    description.serverGroupName = SERVER_GROUP
    description.region = REGION
    description.appName = "myapp"
    description.credentials = credentials

    when:
    new TerminateAzureInstanceAndDecrementServerGroupAtomicOperation(description).operate([])

    then:
    1 * computeClient.deleteInstances("myapp-westus", SERVER_GROUP, ["${SERVER_GROUP}_3"])
    0 * computeClient.reimageInstances(_, _, _)
  }

  void "falls back to asgName when serverGroupName is absent"() {
    given:
    def description = new AzureInstanceDescription()
    description.instance = "${SERVER_GROUP}_3"
    description.asgName = SERVER_GROUP
    description.region = REGION
    description.appName = "myapp"
    description.credentials = credentials

    when:
    new TerminateAzureInstanceAndDecrementServerGroupAtomicOperation(description).operate([])

    then:
    1 * computeClient.deleteInstances("myapp-westus", SERVER_GROUP, _)
  }
}
