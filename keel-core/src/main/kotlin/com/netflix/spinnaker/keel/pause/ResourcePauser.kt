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
import com.netflix.spinnaker.keel.events.ResourceActuationResumed
import com.netflix.spinnaker.keel.persistence.PausedRepository
import com.netflix.spinnaker.keel.persistence.PausedRepository.Scope
import com.netflix.spinnaker.keel.persistence.PausedRepository.Scope.APPLICATION
import com.netflix.spinnaker.keel.persistence.PausedRepository.Scope.RESOURCE
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ResourcePauser(
  val resourceRepository: ResourceRepository,
  val pausedRepository: PausedRepository,
  val publisher: ApplicationEventPublisher
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
  }

  fun resumeApplication(application: String) {
    log.info("Resuming application $application")
    pausedRepository.resumeApplication(application)
    resourceRepository.getResourcesByApplication(application)
      .forEach {
        // helps a user not be confused by an out of date status from before a pause
        publisher.publishEvent(ResourceActuationResumed(it))
      }
  }

  fun pauseResource(id: String) {
    log.info("Pausing resource $id")
    pausedRepository.pauseResource(id)
  }

  fun resumeResource(id: String) {
    log.info("Resuming resource $id")
    pausedRepository.resumeResource(id)
    // helps a user not be confused by an out of date status from before a pause
    publisher.publishEvent(ResourceActuationResumed(resourceRepository.get(id)))
  }

  fun pausedApplications(): List<String> =
    pausedRepository.getPausedApplications()
}
