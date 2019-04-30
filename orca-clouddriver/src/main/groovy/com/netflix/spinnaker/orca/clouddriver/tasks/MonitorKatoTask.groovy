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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.Task
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import retrofit.RetrofitError

import java.time.Clock
import java.util.concurrent.TimeUnit

@Component
@CompileStatic
class MonitorKatoTask implements RetryableTask {

  /**
   * How long to continue trying to look up a task that reports a 404 Not Found.
   *
   * Allows for replication lag if reading tasks from a read-replica of the clouddriver main redis.
   */
  @Value('${tasks.monitorKatoTask.taskNotFoundTimeoutMs:120000}')
  long taskNotFoundTimeoutMs

  private final Clock clock
  private final Registry registry

  @Autowired
  public MonitorKatoTask(Registry registry) {
    this(registry, Clock.systemUTC())
  }

  MonitorKatoTask(Registry registry, Clock clock) {
    this.registry = registry
    this.clock = clock
  }

  long getBackoffPeriod() { 5000L }

  long getTimeout() { 3600000L }

  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    TaskId taskId = stage.context."kato.last.task.id" as TaskId
    if (!taskId) {
      return TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)
    }

    Task katoTask
    def skipReplica = stage.context."kato.task.skipReplica" ?: false
    try {
      katoTask = kato.lookupTask(taskId.id, skipReplica as Boolean).toBlocking().first()
    } catch (RetrofitError re) {
      //handle a 404 if a task update has not successfully replicated to a read replica
      if (re.kind == RetrofitError.Kind.HTTP && re.response.status == HttpURLConnection.HTTP_NOT_FOUND) {
        def firstNotFoundRetry = stage.context."kato.task.firstNotFoundRetry" as Long

        def now = clock.millis()
        def ctx = [:]
        if (firstNotFoundRetry == null || firstNotFoundRetry == -1) {
          ctx['kato.task.firstNotFoundRetry'] = now
          firstNotFoundRetry = now
        }

        if (now - firstNotFoundRetry > taskNotFoundTimeoutMs) {
          if (skipReplica) {
            // immediately fail the first time it gets a 404 directly from the master
            throw re
          }

          registry.counter("monitorKatoTask.taskNotFound.timeout").increment()
          ctx['kato.task.skipReplica'] = true
        }

        registry.counter("monitorKatoTask.taskNotFound.retry").increment()
        ctx['kato.task.notFoundRetryCount'] = ((stage.context."kato.task.notFoundRetryCount" as Integer) ?: 0) + 1
        return TaskResult.builder(ExecutionStatus.RUNNING).context(ctx).build()
      } else {
        throw re
      }
    }


    def katoResultExpected = (stage.context["kato.result.expected"] as Boolean) ?: false
    ExecutionStatus status = katoStatusToTaskStatus(katoTask, katoResultExpected)

    if (status != ExecutionStatus.TERMINAL && status != ExecutionStatus.SUCCEEDED) {
      status = ExecutionStatus.RUNNING
    }

    def outputs = [
      'kato.task.firstNotFoundRetry': -1L,
      'kato.task.notFoundRetryCount': 0
    ] as Map<String, ?>

    if (status == ExecutionStatus.SUCCEEDED) {
      def deployed = getDeployedNames(katoTask)
      // The below two checks aren't mutually exclusive since both `deploy.server.groups` and `deploy.jobs` can initially
      // by empty, although only one of them needs to be filled.
      if (!stage.context.containsKey("deploy.server.groups")) {
        outputs["deploy.server.groups"] = getServerGroupNames(katoTask)
      }
      if (!stage.context.containsKey("deploy.jobs") && deployed) {
        outputs["deploy.jobs"] = deployed
      }
    }
    if (status == ExecutionStatus.SUCCEEDED || status == ExecutionStatus.TERMINAL || status == ExecutionStatus.RUNNING) {
      List<Map<String, Object>> katoTasks = []
      if (stage.context.containsKey("kato.tasks")) {
        katoTasks = stage.context."kato.tasks" as List<Map<String, Object>>
      }
      katoTasks.removeIf { it.id == katoTask.id } // replace with updated version
      Map<String, Object> m = [
        id           : katoTask.id,
        status       : katoTask.status,
        history      : katoTask.history,
        resultObjects: katoTask.resultObjects
      ]
      if (katoTask.resultObjects?.find { it.type == "EXCEPTION" }) {
        def exception = katoTask.resultObjects.find { it.type == "EXCEPTION" }
        m.exception = exception
      }
      katoTasks << m
      outputs["kato.tasks"] = katoTasks

    }

    TaskResult.builder(status).context(outputs).build()
  }

  private static ExecutionStatus katoStatusToTaskStatus(Task katoTask, boolean katoResultExpected) {
    def katoStatus = katoTask.status
    if (katoStatus.failed) {
      return ExecutionStatus.TERMINAL
    } else if (katoStatus.completed) {
      if (katoResultExpected && !katoTask.resultObjects) {
        return ExecutionStatus.RUNNING
      }
      return ExecutionStatus.SUCCEEDED
    } else {
      return ExecutionStatus.RUNNING
    }
  }

  /**
   * @param The task being inspected for region/server group mappings.
   * @return Server group names keyed by region.
   * @deprecate In favor of getDeployedNames(Task task).
   */
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

  private static Map<String, List<String>> getDeployedNames(Task task) {
    Map result = task.resultObjects?.find {
      it?.deployedNamesByLocation
    } ?: [:]

    return (Map<String, List<String>>) result.deployedNamesByLocation
  }
}
