/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.pause

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.events.ApplicationActuationPaused
import com.netflix.spinnaker.keel.events.ApplicationActuationResumed
import com.netflix.spinnaker.keel.events.PersistentEvent
import com.netflix.spinnaker.keel.events.ResourceActuationPaused
import com.netflix.spinnaker.keel.events.ResourceActuationResumed
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.pause.PauseScope.APPLICATION
import com.netflix.spinnaker.keel.pause.PauseScope.RESOURCE
import com.netflix.spinnaker.keel.persistence.PausedRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ActuationPauser(
  val resourceRepository: ResourceRepository,
  val pausedRepository: PausedRepository,
  val publisher: ApplicationEventPublisher,
  val clock: Clock
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  fun isPaused(resource: Resource<*>): Boolean =
    getPauseScope(resource) != null

  fun isPaused(id: String): Boolean {
    val resource = resourceRepository.get(id)
    return isPaused(resource)
  }

  fun getPauseScope(resource: Resource<*>): PauseScope? =
    when {
      applicationIsPaused(resource.application) -> APPLICATION
      resourceIsPaused(resource.id) -> RESOURCE
      else -> null
    }

  fun applicationIsPaused(application: String): Boolean =
    pausedRepository.applicationPaused(application)

  fun resourceIsPaused(id: String): Boolean =
    pausedRepository.resourcePaused(id)

  fun pauseApplication(application: String, user: String) {
    log.info("Pausing application $application")
    pausedRepository.pauseApplication(application, user)
    publisher.publishEvent(ApplicationActuationPaused(application, user, clock))
  }

  fun resumeApplication(application: String, user: String) {
    log.info("Resuming application $application")
    pausedRepository.resumeApplication(application)
    publisher.publishEvent(ApplicationActuationResumed(application, user, clock))
  }

  fun pauseResource(id: String, user: String) {
    log.info("Pausing resource $id")
    pausedRepository.pauseResource(id, user)
    publisher.publishEvent(ResourceActuationPaused(resourceRepository.get(id), user, clock))
  }

  fun resumeResource(id: String, user: String) {
    log.info("Resuming resource $id")
    pausedRepository.resumeResource(id)
    publisher.publishEvent(ResourceActuationResumed(resourceRepository.get(id), user, clock))
  }

  fun pausedApplications(): List<String> =
    pausedRepository.getPausedApplications()

  /**
   * Adds [ApplicationActuationPaused] and [ApplicationActuationResumed] events to the resource event history so that
   * it reflects actuation pauses/resumes at the application level.
   */
  fun addApplicationActuationEvents(originalEvents: List<ResourceEvent>, resource: Resource<*>) =
    mutableListOf<PersistentEvent>()
      .let { events ->
        events.addAll(originalEvents)

        val oldestResourceEvent = events.last()

        if (oldestResourceEvent is ResourceCreated) {
          val appPause = pausedRepository.getPause(APPLICATION, resource.application)
          val resourcePause = pausedRepository.getPause(RESOURCE, resource.id)

          // If the app was paused before the resource was created, and hasn't yet been resumed,
          // we add that event to the history so the resource event history makes sense against its status
          if (appPause != null && appPause.pausedAt.isBefore(oldestResourceEvent.timestamp)) {
            events.add(ApplicationActuationPaused(resource.application, appPause.pausedAt, appPause.pausedBy))
          }

          // Similarly, if the resource itself had been paused, then was deleted and recreated,
          // we add a corresponding event to the history
          if (resourcePause != null && resourcePause.pausedAt.isBefore(oldestResourceEvent.timestamp)) {
            events.add(ResourceActuationPaused(resource, resourcePause.pausedAt, resourcePause.pausedBy))
          }
        }

        // Add all other app paused/resumed events from after the oldest resource event
        resourceRepository
          .applicationEventHistory(resource.application, oldestResourceEvent.timestamp)
          .filter { it is ApplicationActuationPaused || it is ApplicationActuationResumed }
          .also { relevantAppEvents ->
            events.addAll(relevantAppEvents)
          }

        // Always sort by timestamp to put events in the right positions
        events.sortedByDescending { it.timestamp }
      }
}
