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
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.persistence.PausedRepository
import com.netflix.spinnaker.keel.persistence.PausedRepository.Scope
import com.netflix.spinnaker.keel.persistence.PausedRepository.Scope.APPLICATION
import com.netflix.spinnaker.keel.persistence.PausedRepository.Scope.RESOURCE
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

  fun getPauseScope(resource: Resource<*>): Scope? =
    when {
      applicationIsPaused(resource.application) -> APPLICATION
      resourceIsPaused(resource.id) -> RESOURCE
      else -> null
    }

  fun applicationIsPaused(application: String): Boolean =
    pausedRepository.applicationPaused(application)

  fun resourceIsPaused(id: String): Boolean =
    pausedRepository.resourcePaused(id)

  fun pauseApplication(application: String) {
    log.info("Pausing application $application")
    pausedRepository.pauseApplication(application)
    publisher.publishEvent(ApplicationActuationPaused(application, clock))
  }

  fun resumeApplication(application: String) {
    log.info("Resuming application $application")
    pausedRepository.resumeApplication(application)
    publisher.publishEvent(ApplicationActuationResumed(application, clock))
  }

  fun pauseResource(id: String) {
    log.info("Pausing resource $id")
    pausedRepository.pauseResource(id)
    publisher.publishEvent(ResourceActuationPaused(resourceRepository.get(id), clock))
  }

  fun resumeResource(id: String) {
    log.info("Resuming resource $id")
    pausedRepository.resumeResource(id)
    // helps a user not be confused by an out of date status from before a pause
    publisher.publishEvent(ResourceActuationResumed(resourceRepository.get(id), clock))
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
        val relevantAppEvents = resourceRepository
          .applicationEventHistory(resource.application, events.last().timestamp)
          .filter { it is ApplicationActuationPaused || it is ApplicationActuationResumed }
        events.addAll(relevantAppEvents)
        events.sortedByDescending { it.timestamp }
      }
}
