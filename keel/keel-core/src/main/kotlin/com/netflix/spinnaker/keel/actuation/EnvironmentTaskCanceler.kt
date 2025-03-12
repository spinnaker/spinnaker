package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.TaskTrackingRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Handles finding and canceling relevent in flight tasks when a user takes a pin or veto action.
 */
@Component
class EnvironmentTaskCanceler(
  val taskTrackingRepository: TaskTrackingRepository,
  val keelRepository: KeelRepository,
  val taskLauncher: TaskLauncher
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * When a user vetos a version they want that version gone from the environment as quickly as possible.
   *
   * This function finds any in flight tasks that are deploying the vetoed version to the relevant
   *  resources, and cancels them.
   */
  fun cancelTasksForVeto(
    application: String,
    veto: EnvironmentArtifactVeto,
    user: String
  ) {
    val inFlightTasks = taskTrackingRepository.getInFlightTasks(application, veto.targetEnvironment)
    val relevantResources: List<String> = getRelevantResourceIds(application, veto.targetEnvironment, veto.reference)
    // for a veto, we want to cancel tasks that are deploying the vetoed version
    val tasksToCancel = inFlightTasks
      .filter { it.resourceId in relevantResources && it.artifactVersion == veto.version }
      .map { it.id }

    log.info("Canceling tasks $tasksToCancel in application $application because of a veto: {}", veto)

    cancelTasks(tasksToCancel, user)
  }

  /**
   * When a user pins they want that version deployed as quickly as possible.
   *
   * This function finds any in flight tasks that are deploying _different_ versions to the relevant
   *  resources, and cancels them.
   */
  fun cancelTasksForPin(
    application: String,
    pin: EnvironmentArtifactPin,
    user: String
  ) {
    val inFlightTasks = taskTrackingRepository.getInFlightTasks(application, pin.targetEnvironment)
    val relevantResources: List<String> = getRelevantResourceIds(application, pin.targetEnvironment, pin.reference)

    // for a pin, we want to cancel tasks that are NOT deploying the pinned version
    val tasksToCancel = inFlightTasks
      .filter { it.resourceId in relevantResources && it.artifactVersion != pin.version }
      .map { it.id }

    log.info("Canceling tasks $tasksToCancel in application $application because of a pin: {}", pin)

    cancelTasks(tasksToCancel, user)
  }

  fun getRelevantResourceIds(
    application: String,
    environmentName: String,
    artifactReference: String
  ): List<String> {
    val config = keelRepository.getDeliveryConfigForApplication(application)
    val env = config.environmentNamed(environmentName)
    return config.matchingArtifactByReference(artifactReference)?.resourcesUsing(env) ?: emptyList()
  }

  private fun cancelTasks(taskIds: List<String>, user: String) {
    if (taskIds.isNotEmpty()) {
      runBlocking { taskLauncher.cancelTasks(taskIds, user) }
    }
  }
}
