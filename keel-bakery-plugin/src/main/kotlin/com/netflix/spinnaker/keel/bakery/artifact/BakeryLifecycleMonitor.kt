package com.netflix.spinnaker.keel.bakery.artifact

import com.netflix.spinnaker.config.LifecycleConfig
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.FAILED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.SUCCEEDED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.UNKNOWN
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType.BAKE
import com.netflix.spinnaker.keel.lifecycle.LifecycleMonitor
import com.netflix.spinnaker.keel.lifecycle.LifecycleMonitorRepository
import com.netflix.spinnaker.keel.lifecycle.MonitoredTask
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.BUFFERED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.RUNNING
import com.netflix.spinnaker.keel.orca.OrcaService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * A monitor for bake tasks.
 *
 * This class expects the link from [MonitoredTask] to be an orca task id.
 * It uses this to fetch the execution status for that task.
 * It emits [LifecycleEvent]s based on the execution status.
 *
 * When the task is complete [SUCCEEDED, FAILED], it removes the [MonitoredTask]
 *   from the [LifecycleMonitorRepository].
 */
@Component
@EnableConfigurationProperties(LifecycleConfig::class)
class BakeryLifecycleMonitor(
  override val monitorRepository: LifecycleMonitorRepository,
  override val publisher: ApplicationEventPublisher,
  override val lifecycleConfig: LifecycleConfig,
  private val orcaService: OrcaService,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String
): LifecycleMonitor(monitorRepository, publisher, lifecycleConfig) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun handles(type: LifecycleEventType): Boolean =
    type == BAKE

  override suspend fun monitor(task: MonitoredTask) {
    kotlin.runCatching {
      orcaService.getOrchestrationExecution(task.link, DEFAULT_SERVICE_ACCOUNT)
    }
      .onSuccess { execution ->
        when {
          execution.status == BUFFERED -> publishCorrectLink(task)
          execution.status == RUNNING -> publishRunningEvent(task)
          execution.status.isSuccess() -> publishSucceededEvent(task)
          execution.status.isFailure() -> publishFailedEvent(task)
          else -> publishUnknownEvent(task, execution)
        }

        if (execution.status.isComplete()) {
          endMonitoringOfTask(task)
        } else {
          markSuccessFetchingStatus(task)
        }
      }
      .onFailure { exception ->
        log.error("Error fetching status for $task: ", exception)
        handleFailureFetchingStatus(task)
      }
  }

  private fun orcaTaskIdToLink(task: MonitoredTask): String =
    "$spinnakerBaseUrl/#/tasks/${task.link}"

  private fun publishCorrectLink(task: MonitoredTask) {
    task.publishEvent(
      task.triggeringEvent.status,
      task.triggeringEvent.text,
    )
  }

  private fun publishRunningEvent(task: MonitoredTask) {
    task.publishEvent(
      LifecycleEventStatus.RUNNING,
      "Bake running for version ${task.triggeringEvent.artifactVersion}",
    )
  }

  private fun publishSucceededEvent(task: MonitoredTask) {
    task.publishEvent(
      SUCCEEDED,
      "Bake succeeded for version ${task.triggeringEvent.artifactVersion}",
    )
  }

  private fun publishFailedEvent(task: MonitoredTask) {
    task.publishEvent(
      FAILED,
      "Bake failed for version ${task.triggeringEvent.artifactVersion}"
    )
  }

  override fun publishExceptionEvent(task: MonitoredTask) {
    task.publishEvent(
      UNKNOWN,
      "Failed to monitor bake of version ${task.triggeringEvent.artifactVersion}" +
        " because we could not get the status ${lifecycleConfig.numFailuresAllowed} times. Status unknown.",
    )
  }

  private fun publishUnknownEvent(task: MonitoredTask, execution: ExecutionDetailResponse) {
    log.warn("Monitored bake ${task.triggeringEvent} in an unhandled status (${execution.status}")
    task.publishEvent(
      UNKNOWN,
      "Bake status unknown for version ${task.triggeringEvent.artifactVersion}"
    )
  }

  fun MonitoredTask.publishEvent(status: LifecycleEventStatus, text: String?) {
    publisher.publishEvent(triggeringEvent.copy(
      status = status,
      link = orcaTaskIdToLink(this),
      text = text,
      startMonitoring = false,
      timestamp = null // let repository record the current time
    ))
  }
}
