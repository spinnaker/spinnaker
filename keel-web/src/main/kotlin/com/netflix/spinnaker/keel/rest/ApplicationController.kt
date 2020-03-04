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

import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.ResourceStatus.PAUSED
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/application"])
class ApplicationController(
  private val repository: KeelRepository,
  private val actuationPauser: ActuationPauser
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @GetMapping(
    path = ["/{application}"],
    produces = [APPLICATION_JSON_VALUE]
  )
  fun get(
    @PathVariable("application") application: String,
    @RequestParam("includeDetails", required = false, defaultValue = "false") includeDetails: Boolean
  ): Map<String, Any> {
    if (includeDetails) {
      var resources = repository.getSummaryByApplication(application)
      resources = resources.map { summary ->
        if (actuationPauser.resourceIsPaused(summary.id)) {
          // we only update the status if the individual resource is paused,
          // because the application pause is reflected in the response as a top level key.
          summary.copy(status = PAUSED)
        } else {
          summary
        }
      }
      val constraintStates = repository.constraintStateFor(application)

      return mapOf(
        "applicationPaused" to actuationPauser.applicationIsPaused(application),
        "hasManagedResources" to resources.isNotEmpty(),
        "resources" to resources,
        "currentEnvironmentConstraints" to constraintStates
      )
    }
    return mapOf(
      "applicationPaused" to actuationPauser.applicationIsPaused(application),
      "hasManagedResources" to repository.hasManagedResources(application)
    )
  }

  @PostMapping(
    path = ["/{application}/pause"]
  )
  fun pause(@PathVariable("application") application: String) {
    actuationPauser.pauseApplication(application)
  }

  @DeleteMapping(
    path = ["/{application}/pause"]
  )
  fun resume(@PathVariable("application") application: String) {
    actuationPauser.resumeApplication(application)
  }
}
