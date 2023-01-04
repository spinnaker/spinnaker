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
import com.netflix.spinnaker.kork.annotations.VisibleForTesting
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.Task
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.SystemNotification
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

import javax.annotation.Nonnull
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit

@Slf4j
@Component
@CompileStatic
class MonitorKatoTask implements RetryableTask, CloudProviderAware {

  private final Clock clock
  private final Registry registry
  private final KatoService kato
  private final DynamicConfigService dynamicConfigService
  private final RetrySupport retrySupport
  private static final int MAX_NOTFOUND_RETRIES = 30

  @VisibleForTesting
  static final int MAX_HTTP_INTERNAL_RETRIES = 5

  @Autowired
  MonitorKatoTask(KatoService katoService, Registry registry, DynamicConfigService dynamicConfigService, RetrySupport retrySupport) {
    this(katoService, registry, Clock.systemUTC(), dynamicConfigService, retrySupport)
  }

  @VisibleForTesting
  MonitorKatoTask(KatoService katoService, Registry registry, Clock clock, DynamicConfigService dynamicConfigService, RetrySupport retrySupport) {
    this.registry = registry
    this.clock = clock
    this.kato = katoService
    this.dynamicConfigService = dynamicConfigService
    this.retrySupport = retrySupport
  }

  long getBackoffPeriod() { 5000L }

  long getTimeout() { 3600000L }

  @Override
  long getDynamicBackoffPeriod(StageExecution stage, Duration taskDuration) {
    if ((stage.context."kato.task.lastStatus" as ExecutionStatus) == ExecutionStatus.TERMINAL) {
      return Math.max(backoffPeriod, TimeUnit.MINUTES.toMillis(2))
    }
    return backoffPeriod
  }

  @Override
  TaskResult onTimeout(@Nonnull StageExecution stage) {
    monitorFinalTerminalRetry(stage, "timeout")
    return null
  }

  @Override
  TaskResult execute(StageExecution stage) {
    TaskId taskId = stage.context."kato.last.task.id" as TaskId
    if (!taskId) {
      return TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)
    }

    Task katoTask
    def outputs = [
        'kato.task.terminalRetryCount': 0,
        'kato.task.firstNotFoundRetry': -1L,
        'kato.task.notFoundRetryCount': 0
    ] as Map<String, ?>

    try {
      retrySupport.retry({
        katoTask = kato.lookupTask(taskId.id, false)
      }, MAX_HTTP_INTERNAL_RETRIES, Duration.ofMillis(100), false)
      outputs['kato.task.notFoundRetryCount'] = 0
    } catch (RetrofitError re) {
      if (re.kind == RetrofitError.Kind.HTTP && re.response.status == HttpURLConnection.HTTP_NOT_FOUND) {
        def notFoundRetryCount = ((stage.context."kato.task.notFoundRetryCount" as Long) ?: 0) + 1

        def ctx = ['kato.task.notFoundRetryCount': notFoundRetryCount]
        if (notFoundRetryCount >= MAX_NOTFOUND_RETRIES) {
          throw re
        }

        registry.counter("monitorKatoTask.taskNotFound.retry").increment()
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

    outputs['kato.task.lastStatus'] = status

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

      if (stage.context."kato.task.retriedOperation" == true) {
        Integer totalRetries = stage.context."kato.task.terminalRetryCount" as Integer
        log.info("Completed kato task ${katoTask.id} (total retries: ${totalRetries}) after exception: {}", getException(katoTask))
        ((PipelineExecutionImpl) stage.execution).systemNotifications.add(new SystemNotification(
          clock.millis(),
          "katoRetryTask",
          "Completed cloud provider retry",
          true
        ))
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
        resultObjects: katoTask.resultObjects,
        outputs      : katoTask.outputs
      ]
      if (katoTask.resultObjects?.find { it.type == "EXCEPTION" }) {
        def exception = katoTask.resultObjects.find { it.type == "EXCEPTION" }
        m.exception = exception
      }
      katoTasks << m
      outputs["kato.tasks"] = katoTasks
    }

    if (shouldRetry(katoTask, status)) {
      ((PipelineExecutionImpl) stage.execution).systemNotifications.add(
        new SystemNotification(
        clock.millis(),
        "katoRetryTask",
        "Retrying failed downstream cloud provider operation",
        false
      ))
      try {
        kato.resumeTask(katoTask.id)
      } catch (Exception e) {
        if (e instanceof RetrofitError) {
          RetrofitError retrofitError = (RetrofitError) e
          if (retrofitError?.response?.status == 404) {
            monitorFinalTerminalRetry(stage, "404")
            // unexpected -- no sense attempting to resume a saga that `clouddriver` has no knowledge about
            throw e
          }
        }

        // Swallow the exception; we'll let Orca retry the next time around.
        log.error("Request failed attempting to resume task", e)
      }
      status = ExecutionStatus.RUNNING

      Integer retryCount = ((stage.context."kato.task.terminalRetryCount" as Integer) ?: 0) + 1
      outputs["kato.task.terminalRetryCount"] = retryCount

      stage.context."kato.task.retriedOperation" = true

      log.info("Retrying kato task ${katoTask.id} (retry: ${retryCount}) with exception: {}", getException(katoTask))
    }

    return TaskResult.builder(status).context(outputs).build()
  }

  private boolean shouldRetry(Task katoTask, ExecutionStatus status) {
    return (
      status == ExecutionStatus.TERMINAL
      && katoTask.status.retryable
      && dynamicConfigService.isEnabled("tasks.monitor-kato-task.terminal-retries", true)
    )
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
   * Log and emits a metric when a kato task retry is finally terminal - typically either from
   * the task timing out or from an unexpected error, like a 404, when attempting to retry.
   */
  private void monitorFinalTerminalRetry(StageExecution stage, String reason) {
    if (stage.context."kato.task.retriedOperation" == true) {
      TaskId taskId = stage.context."kato.last.task.id" as TaskId
      Integer totalRetries = stage.context."kato.task.terminalRetryCount" as Integer
      log.warn("Failed retrying kato task '{}' (retries: '{}') due to reason: '{}'", taskId.id,
          totalRetries, reason)
      registry.counter("monitorKatoTask.terminalRetry", "reason", reason).increment()
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

  private static Map getException(Task task) {
    return task.resultObjects?.find { it.type == "EXCEPTION" } ?: [:]
  }
}
