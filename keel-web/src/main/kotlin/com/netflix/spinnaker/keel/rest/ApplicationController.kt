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
package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.constraints.ConstraintState
import com.netflix.spinnaker.keel.constraints.UpdatedConstraintStatus
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.events.ApplicationEvent
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.ResourceRepository.Companion.DEFAULT_MAX_EVENTS
import com.netflix.spinnaker.keel.services.ApplicationService
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/application"])
class ApplicationController(
  private val actuationPauser: ActuationPauser,
  private val applicationService: ApplicationService
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @GetMapping(
    path = ["/{application}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('READ', 'APPLICATION', #application)
    and @authorizationSupport.hasCloudAccountPermission('READ', 'APPLICATION', #application)"""
  )
  fun get(
    @PathVariable("application") application: String,
    @RequestParam("includeDetails", required = false, defaultValue = "false") includeDetails: Boolean,
    @RequestParam("entities", required = false, defaultValue = "") entities: MutableList<String>
  ): Map<String, Any> {
    return mutableMapOf(
      "applicationPaused" to actuationPauser.applicationIsPaused(application),
      "hasManagedResources" to applicationService.hasManagedResources(application),
      "currentEnvironmentConstraints" to applicationService.getConstraintStatesFor(application)
    ).also { results ->
      // for backwards-compatibility
      if (includeDetails && !entities.contains("resources")) {
        entities.add("resources")
      }
      entities.forEach { entity ->
        results[entity] = when (entity) {
          "resources" -> applicationService.getResourceSummariesFor(application)
          "environments" -> applicationService.getEnvironmentSummariesFor(application)
          "artifacts" -> applicationService.getArtifactSummariesFor(application)
          else -> throw InvalidRequestException("Unknown entity type: $entity")
        }
      }
    }
  }

  @GetMapping(
    path = ["/{application}/config"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('READ', 'APPLICATION', #application)""")
  fun getConfigByApplication(
    @PathVariable("application") application: String
  ): DeliveryConfig = applicationService.getDeliveryConfig(application)

  @DeleteMapping(
    path = ["/{application}/config"]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #application)"""
  )
  fun deleteConfigByApp(@PathVariable("application") application: String) {
    applicationService.deleteConfigByApp(application)
  }

  @PostMapping(
    path = ["/{application}/environment/{environment}/constraint"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #application)"""
  )
  fun updateConstraintStatus(
    @RequestHeader("X-SPINNAKER-USER") user: String,
    @PathVariable("application") application: String,
    @PathVariable("environment") environment: String,
    @RequestBody status: UpdatedConstraintStatus
  ) {
    applicationService.updateConstraintStatus(user, application, environment, status)
  }

  @GetMapping(
    path = ["/{application}/environment/{environment}/constraints"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("@authorizationSupport.hasApplicationPermission('READ', 'APPLICATION', #application)")
  fun getConstraintState(
    @PathVariable("application") application: String,
    @PathVariable("environment") environment: String,
    @RequestParam("limit") limit: Int?
  ): List<ConstraintState> =
    applicationService.getConstraintStatesFor(application, environment, limit ?: DEFAULT_MAX_EVENTS)

  @PostMapping(
    path = ["/{application}/pause"]
  )
  @PreAuthorize("@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #application)")
  fun pause(
    @PathVariable("application") application: String,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ) {
    actuationPauser.pauseApplication(application, user)
  }

  @DeleteMapping(
    path = ["/{application}/pause"]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #application)"""
  )
  fun resume(
    @PathVariable("application") application: String,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ) {
    actuationPauser.resumeApplication(application, user)
  }

  @PostMapping(
    path = ["/{application}/pin"]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #application)"""
  )
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun pin(
    @RequestHeader("X-SPINNAKER-USER") user: String,
    @PathVariable("application") application: String,
    @RequestBody pin: EnvironmentArtifactPin
  ) {
    applicationService.pin(user, application, pin)
  }

  @DeleteMapping(
    path = ["/{application}/pin/{targetEnvironment}"]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #application)"""
  )
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun deletePin(
    @RequestHeader("X-SPINNAKER-USER") user: String,
    @PathVariable("application") application: String,
    @PathVariable("targetEnvironment") targetEnvironment: String,
    @RequestParam reference: String? = null
  ) {
    applicationService.deletePin(user, application, targetEnvironment, reference)
  }

  @PostMapping(
    path = ["/{application}/veto"]
  )
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #application)"""
  )
  fun veto(
    @RequestHeader("X-SPINNAKER-USER") user: String,
    @PathVariable("application") application: String,
    @RequestBody veto: EnvironmentArtifactVeto
  ) {
    applicationService.markAsVetoedIn(user, application, veto, true)
  }

  @DeleteMapping(
    path = ["/{application}/veto/{targetEnvironment}/{reference}/{version}"]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #application)"""
  )
  fun deleteVeto(
    @RequestHeader("X-SPINNAKER-USER") user: String,
    @PathVariable("application") application: String,
    @PathVariable("targetEnvironment") targetEnvironment: String,
    @PathVariable("reference") reference: String,
    @PathVariable("version") version: String
  ) {
    applicationService.deleteVeto(application, targetEnvironment, reference, version)
  }

  @GetMapping(
    path = ["/{application}/events"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('READ', 'APPLICATION', #application)""")
  fun getEvents(
    @PathVariable("application") application: String,
    @RequestParam("limit") limit: Int?
  ): List<ApplicationEvent> = applicationService.getApplicationEventHistory(application, limit ?: DEFAULT_MAX_EVENTS)
}
