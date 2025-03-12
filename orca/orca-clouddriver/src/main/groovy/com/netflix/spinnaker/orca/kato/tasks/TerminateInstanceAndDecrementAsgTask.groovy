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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
@Deprecated
class TerminateInstanceAndDecrementAsgTask implements Task {
  @Autowired
  KatoService kato

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    def taskId = kato.requestOperations([[terminateInstanceAndDecrementAsgDescription: stage.context]])

    TaskResult.builder(ExecutionStatus.SUCCEEDED).context([
      "notification.type"     : "terminateinstanceanddecrementasg",
      "terminate.account.name": stage.context.credentials,
      "terminate.region"      : stage.context.region,
      "kato.last.task.id"     : taskId,
      "kato.task.id"          : taskId, // TODO retire this.
      "terminate.instance.ids": [stage.context.instance],
    ]).build()
  }

}
