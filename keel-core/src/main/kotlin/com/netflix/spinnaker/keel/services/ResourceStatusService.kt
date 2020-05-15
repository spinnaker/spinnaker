package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.events.ApplicationActuationResumed
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceActuationResumed
import com.netflix.spinnaker.keel.events.ResourceActuationVetoed
import com.netflix.spinnaker.keel.events.ResourceCheckError
import com.netflix.spinnaker.keel.events.ResourceCheckUnresolvable
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceHistoryEvent
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.ResourceTaskFailed
import com.netflix.spinnaker.keel.events.ResourceTaskSucceeded
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceStatus
import com.netflix.spinnaker.keel.persistence.ResourceStatus.ACTUATING
import com.netflix.spinnaker.keel.persistence.ResourceStatus.CREATED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.CURRENTLY_UNRESOLVABLE
import com.netflix.spinnaker.keel.persistence.ResourceStatus.DIFF
import com.netflix.spinnaker.keel.persistence.ResourceStatus.ERROR
import com.netflix.spinnaker.keel.persistence.ResourceStatus.HAPPY
import com.netflix.spinnaker.keel.persistence.ResourceStatus.PAUSED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.RESUMED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.UNHAPPY
import com.netflix.spinnaker.keel.persistence.ResourceStatus.UNKNOWN
import com.netflix.spinnaker.keel.persistence.ResourceStatus.VETOED
import org.springframework.stereotype.Component

/**
 * Service object that offers high-level APIs around resource (event) history and status.
 */
@Component
class ResourceStatusService(
  private val resourceRepository: ResourceRepository,
  private val actuationPauser: ActuationPauser
) {

  /**
   * Returns the status of the specified resource by first checking whether or not it or the parent application are
   * paused, then looking into the last few events in the resource's history.
   */
  fun getStatus(id: String): ResourceStatus {
    // For the PAUSED status, we look at the `paused` table as opposed to events, since these records
    // persist even when a delivery config/resource (and associated events) have been deleted. We do
    // this so we don't inadvertently start actuating on a resource that had been previously paused,
    // without explicit action from the user to resume.
    if (actuationPauser.isPaused(id)) {
      return PAUSED
    }

    val history = resourceRepository.eventHistory(id, 10)
    return when {
      history.isEmpty() -> UNKNOWN // shouldn't happen, but is a safeguard since events are persisted asynchronously
      history.isHappy() -> HAPPY
      history.isUnhappy() -> UNHAPPY
      history.isDiff() -> DIFF
      history.isActuating() -> ACTUATING
      history.isError() -> ERROR
      history.isCreated() -> CREATED
      history.isVetoed() -> VETOED
      history.isResumed() -> RESUMED
      history.isCurrentlyUnresolvable() -> CURRENTLY_UNRESOLVABLE
      else -> UNKNOWN
    }
  }

  private fun List<ResourceHistoryEvent>.isHappy(): Boolean {
    return first() is ResourceValid || first() is ResourceDeltaResolved
  }

  private fun List<ResourceHistoryEvent>.isActuating(): Boolean {
    return first() is ResourceActuationLaunched || first() is ResourceTaskSucceeded ||
      // we might want to move ResourceTaskFailed to isError later on
      first() is ResourceTaskFailed
  }

  private fun List<ResourceHistoryEvent>.isError(): Boolean {
    return first() is ResourceCheckError
  }

  private fun List<ResourceHistoryEvent>.isCreated(): Boolean {
    return first() is ResourceCreated
  }

  private fun List<ResourceHistoryEvent>.isDiff(): Boolean {
    return first() is ResourceDeltaDetected || first() is ResourceMissing
  }

  private fun List<ResourceHistoryEvent>.isVetoed(): Boolean {
    return first() is ResourceActuationVetoed
  }

  private fun List<ResourceHistoryEvent>.isResumed(): Boolean {
    return first() is ResourceActuationResumed || first() is ApplicationActuationResumed
  }

  private fun List<ResourceHistoryEvent>.isCurrentlyUnresolvable(): Boolean {
    return first() is ResourceCheckUnresolvable
  }

  /**
   * Checks last 10 events for flapping between only ResourceActuationLaunched and ResourceDeltaDetected
   */
  private fun List<ResourceHistoryEvent>.isUnhappy(): Boolean {
    val recentSliceOfHistory = this.subList(0, Math.min(10, this.size))
    val filteredHistory = recentSliceOfHistory.filter { it is ResourceDeltaDetected || it is ResourceActuationLaunched }
    if (filteredHistory.size != recentSliceOfHistory.size) {
      // there are other events, we're not thrashing.
      return false
    }
    return true
  }
}
