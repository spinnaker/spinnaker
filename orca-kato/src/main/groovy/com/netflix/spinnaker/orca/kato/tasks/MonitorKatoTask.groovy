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
import groovy.transform.TypeCheckingMode
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.Task
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class MonitorKatoTask implements RetryableTask {

  long backoffPeriod = 1000
  long timeout = 300000

  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    TaskId taskId = stage.context."kato.last.task.id" as TaskId
    Task katoTask = kato.lookupTask(taskId.id).toBlocking().first()
    ExecutionStatus status = katoStatusToTaskStatus(katoTask.status)

    if (status != ExecutionStatus.TERMINAL && status != ExecutionStatus.SUCCEEDED) {
      status = ExecutionStatus.RUNNING
    }

    Map<String, ? extends Object> outputs = [:]
    if (status == ExecutionStatus.SUCCEEDED && !stage.context.containsKey("deploy.server.groups")) {
      outputs["deploy.server.groups"] = getServerGroupNames(katoTask)
    }
    if (status == ExecutionStatus.SUCCEEDED || status == ExecutionStatus.TERMINAL) {
      List<Map<String, Object>> katoTasks = []
      if (stage.context.containsKey("kato.tasks")) {
        katoTasks = stage.context."kato.tasks" as List<Map<String, Object>>
      }
      Map<String, Object> m = [id           : katoTask.id, status: katoTask.status, history: katoTask.history,
                               resultObjects: katoTask.resultObjects]
      if (katoTask.resultObjects.find { it.type == "EXCEPTION" }) {
        def exception = katoTask.resultObjects.find { it.type == "EXCEPTION" }
        m.exception = exception
      }
      katoTasks << m
      outputs["kato.tasks"] = katoTasks

    }

    new DefaultTaskResult(status, outputs)
  }

  private
  static ExecutionStatus katoStatusToTaskStatus(Task.Status katoStatus) {
    if (katoStatus.failed) {
      return ExecutionStatus.TERMINAL
    } else if (katoStatus.completed) {
      return ExecutionStatus.SUCCEEDED
    } else {
      return ExecutionStatus.RUNNING
    }
  }

  // This is crappy logic
  // TODO clean this up in Kato
  @CompileStatic(TypeCheckingMode.SKIP)
  private static Map<String, List<String>> getServerGroupNames(Task task) {
    def result = [:]
    def resultObjects = task.resultObjects ?: []
    resultObjects.removeAll([null])
    resultObjects.each {
      if (it.serverGroupNames) {
        it.serverGroupNames.each {
          def parts = it.split(':')
          def region = parts[0]
          if (parts.size() > 1) {
            def serverGroup = parts[1]
            if (!result.containsKey(region)) {
              result[region] = []
            }
            result[region] << serverGroup
          } else {
            region = "region_missing"
            def serverGroup = parts[0]
            if (!result.containsKey(region)) {
              result[region] = []
            }
            result[region] << serverGroup
          }
        }
      }
    }
    result
  }
}
