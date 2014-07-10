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

package com.netflix.spinnaker.orca.kato.tasks

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
class MonitorTask implements Task {

  @Autowired
  KatoService kato

  @Override
  TaskResult execute(TaskContext context) {
    TaskId taskId = context.inputs."kato.task.id" as TaskId
    kato.lookupTask(taskId.id).toBlockingObservable().first().with {
      new DefaultTaskResult(katoStatusToTaskStatus(status))
    }
  }

  private TaskResult.Status katoStatusToTaskStatus(com.netflix.spinnaker.orca.kato.api.Task.Status katoStatus) {
    if (katoStatus.failed) {
      return TaskResult.Status.FAILED
    } else if (katoStatus.completed) {
      return TaskResult.Status.SUCCEEDED
    } else {
      return TaskResult.Status.RUNNING
    }
  }
}
