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

import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/application"])
class ApplicationController(
  private val resourceRepository: ResourceRepository,
  private val deliveryConfigRepository: DeliveryConfigRepository
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
    val hasDeliveryConfig = deliveryConfigRepository.hasDeliveryConfig(application)

    if (includeDetails) {
      val resources = resourceRepository.getSummaryByApplication(application)
      val constraintStates = if (hasDeliveryConfig) {
        deliveryConfigRepository.constraintStateFor(application)
      } else {
        emptyList()
      }

      return mapOf(
        "hasManagedResources" to resources.isNotEmpty(),
        "resources" to resources,
        "hasDeliveryConfig" to hasDeliveryConfig,
        "currentEnvironmentConstraints" to constraintStates
      )
    }
    return mapOf(
      "hasManagedResources" to resourceRepository.hasManagedResources(application),
      "hasDeliveryConfig" to hasDeliveryConfig)
  }
}
