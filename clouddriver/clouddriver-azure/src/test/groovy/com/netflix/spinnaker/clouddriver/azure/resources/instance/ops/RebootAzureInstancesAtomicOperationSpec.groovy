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

class RebootAzureInstancesAtomicOperationSpec extends Specification {
  private static final SERVER_GROUP = "myapp-dev-v086"
  private static final REGION = "westus"
  private static final APP_NAME = "myapp"

  AzureComputeClient computeClient = Mock(AzureComputeClient)
  // AzureCredentials.computeClient is a final field, so its getter is final and CGLIB-based
  // Mock() cannot stub it; GroovyMock intercepts via the metaclass.
  AzureCredentials credentials = GroovyMock(AzureCredentials) { getComputeClient() >> computeClient }

  def setup() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  private AzureInstanceDescription description() {
    def description = new AzureInstanceDescription()
    description.instanceIds = ["${SERVER_GROUP}_3".toString()]
    description.serverGroupName = SERVER_GROUP
    description.region = REGION
    description.appName = APP_NAME
    description.credentials = credentials
    description
  }

  void "reboots the requested instances in the derived resource group"() {
    when:
    new RebootAzureInstancesAtomicOperation(description()).operate([])

    then:
    1 * computeClient.rebootInstances("myapp-westus", SERVER_GROUP, ["${SERVER_GROUP}_3"])
  }

  void "derives the app name from the server group when appName is absent"() {
    given:
    def description = description()
    description.appName = null

    when:
    new RebootAzureInstancesAtomicOperation(description).operate([])

    then:
    1 * computeClient.rebootInstances("myapp-westus", SERVER_GROUP, _)
  }

  void "fails when no server group name is supplied"() {
    given:
    def description = description()
    description.serverGroupName = null
    description.asgName = null

    when:
    new RebootAzureInstancesAtomicOperation(description).operate([])

    then:
    thrown(IllegalArgumentException)
    0 * computeClient.rebootInstances(_, _, _)
  }
}
