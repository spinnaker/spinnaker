/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs

import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.api.operations.OperationsInput
import com.netflix.spinnaker.orca.api.operations.OperationsRunner
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.model.KatoOperationsContext
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject

class EcsNativeRollbackServerGroupTaskSpec extends Specification {

  @Subject
  EcsNativeRollbackServerGroupTask task = new EcsNativeRollbackServerGroupTask(retrySupport: new RetrySupport())

  def 'submits an ecs-native updateLaunchConfig op targeting the chosen task definition'() {
    given:
    OperationsInput captured = null
    task.operationsRunner = Mock(OperationsRunner) {
      1 * run(_) >> {
        captured = it[0]
        new KatoOperationsContext(new TaskId('task-1'), null)
      }
    }

    def stage = new StageExecutionImpl(
      PipelineExecutionImpl.newPipeline('orca'),
      'rollbackEcsNativeServerGroup',
      [
        cloudProvider : 'ecs-native',
        credentials   : 'test',
        region        : 'us-west-2',
        serverGroupName: 'myapp',
        taskDefinition: 'arn:aws:ecs:us-west-2:1:task-definition/myapp:7',
        forceNewDeployment: true,
      ])

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    captured.cloudProvider == 'ecs-native'
    def op = captured.operations.first().updateLaunchConfig
    op.serverGroupName == 'myapp'
    op.taskDefinition == 'arn:aws:ecs:us-west-2:1:task-definition/myapp:7'
    op.forceNewDeployment == true
    // deploy.server.groups feeds the following waitForEcsNativeServiceDeployment stage.
    result.context.'deploy.server.groups' == ['us-west-2': ['myapp']]
    result.context.serverGroupName == 'myapp'
  }
}
