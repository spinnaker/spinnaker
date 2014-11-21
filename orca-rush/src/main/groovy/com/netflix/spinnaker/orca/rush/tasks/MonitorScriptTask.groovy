/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.rush.tasks

import com.netflix.spinnaker.orca.rush.api.RushService
import com.netflix.spinnaker.orca.rush.api.ScriptExecution
import com.netflix.spinnaker.orca.rush.api.ScriptId
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.ImmutableStage
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
class MonitorScriptTask implements RetryableTask {

  long backoffPeriod = 1000
  long timeout = 600000

  @Autowired RushService rushService

  @Override
  TaskResult execute(ImmutableStage stage) {
    ScriptId task = stage.context."rush.task.id" as ScriptId
    ScriptExecution execution = rushService.scriptDetails(task.id).toBlocking().single()
    new DefaultTaskResult(rushStatusToTaskStatus(execution), [execution: execution ])
  }

  private static PipelineStatus rushStatusToTaskStatus(ScriptExecution execution) {
    if (execution.status == 'SUCCESSFUL') {
      return PipelineStatus.SUCCEEDED
    } else if(execution.status == 'FAILURE'){
      return PipelineStatus.FAILED
    } else {
      return PipelineStatus.RUNNING
    }
  }
}
