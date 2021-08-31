package com.netflix.spinnaker.keel.bakery.artifact

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.config.LifecycleConfig
import com.netflix.spinnaker.keel.api.TaskStatus.BUFFERED
import com.netflix.spinnaker.keel.api.TaskStatus.RUNNING
import com.netflix.spinnaker.keel.artifacts.BakedImage
import com.netflix.spinnaker.keel.clouddriver.ImageService
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
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.BakedImageRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock

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
@EnableConfigurationProperties(LifecycleConfig::class, BaseUrlConfig::class)
class BakeryLifecycleMonitor(
  override val monitorRepository: LifecycleMonitorRepository,
  override val publisher: ApplicationEventPublisher,
  override val lifecycleConfig: LifecycleConfig,
  private val orcaService: OrcaService,
  private val baseUrlConfig: BaseUrlConfig,
  private val bakedImageRepository: BakedImageRepository,
  private val imageService: ImageService,
  private val objectMapper: ObjectMapper,
  private val clock: Clock
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
          execution.status.isSuccess() -> {
            publishSucceededEvent(task)
            storeBakedImage(execution)
          }
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

  /**
   * Stores the ami details from a successful bake task so we don't have to wait for those
   * details to appear in the clouddriver cache (~5 minute delay every time).
   *
   * If there's a problem parsing we log an error and move on, because we will see
   * the image in clouddriver eventually.
   */
  suspend fun storeBakedImage(execution: ExecutionDetailResponse) {
    // we know we launch a bake task with a stage called "bake".
    val bakeStageRaw = execution.execution?.stages?.find { it["type"] == "bake" }
    if (bakeStageRaw == null) {
      log.error("Trying to find baked ami information, but can't find a bake stage for app ${execution.application} in execution ${execution.id}")
      return
    }
    try {
      val bakeStage = objectMapper.convertValue<BakeStage>(bakeStageRaw)
      val details = bakeStage.outputs.deploymentDetails
      if (details.isEmpty()) {
        log.error("No bake details in the bake stage for app ${execution.application} in execution ${execution.id}")
        return
      }

      // use the first region present because the only difference should be in ami id and region name
      // even the base ami app version will be the same across regions
      val detail = details.first()
      val bakedImage = BakedImage(
        name = detail.imageName,
        baseLabel = detail.baseLabel,
        baseOs = detail.baseOs,
        vmType = detail.vmType,
        cloudProvider = detail.cloudProviderType,
        appVersion = detail.`package`.substringBefore("_all.deb").replaceFirst("_", "-"),
        baseAmiName = imageService.findBaseImageName(detail.baseAmiId, detail.region),
        timestamp = execution.endTime ?: clock.instant(),
        amiIdsByRegion = details.associate { regionDetail -> regionDetail.region to regionDetail.imageId }
      )
      bakedImageRepository.store(bakedImage)
    } catch (e: JsonMappingException) {
      log.error("Error converting bake stage to kotlin object for app ${execution.application} in execution ${execution.id}", e)
    } catch (e: Exception) {
      // if there's an error that's fine, we will move on.
      log.error("Error finding baked image information for app ${execution.application} in execution ${execution.id}", e)
    }
  }

  private fun orcaTaskIdToLink(task: MonitoredTask): String =
    "${baseUrlConfig.baseUrl}/#/tasks/${task.link}"

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
