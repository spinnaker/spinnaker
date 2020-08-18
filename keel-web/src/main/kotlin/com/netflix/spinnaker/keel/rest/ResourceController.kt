/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.core.api.SubmittedResource
import com.netflix.spinnaker.keel.diff.AdHocDiffer
import com.netflix.spinnaker.keel.diff.DiffResult
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.ResourceStatus
import com.netflix.spinnaker.keel.services.ResourceStatusService
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/resources"])
class ResourceController(
  private val repository: KeelRepository,
  private val actuationPauser: ActuationPauser,
  private val adHocDiffer: AdHocDiffer,
  private val resourceStatusService: ResourceStatusService
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @GetMapping(
    path = ["/{id}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('READ', 'RESOURCE', #id)
    and @authorizationSupport.hasCloudAccountPermission('READ', 'RESOURCE', #id)"""
  )
  fun get(@PathVariable("id") id: String): Resource<*> {
    log.debug("Getting: $id")
    return repository.getResource(id)
  }

  @GetMapping(
    path = ["/{id}/status"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('READ', 'RESOURCE', #id)
    and @authorizationSupport.hasCloudAccountPermission('READ', 'RESOURCE', #id)"""
  )
  fun getStatus(@PathVariable("id") id: String): ResourceStatus =
    resourceStatusService.getStatus(id)

  @PostMapping(
    path = ["/{id}/pause"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("@authorizationSupport.hasApplicationPermission('WRITE', 'RESOURCE', #id)")
  fun pauseResource(
    @PathVariable("id") id: String,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ) {
    actuationPauser.pauseResource(id, user)
  }

  @DeleteMapping(
    path = ["/{id}/pause"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('WRITE', 'RESOURCE', #id)
    and @authorizationSupport.hasServiceAccountAccess('RESOURCE', #id)"""
  )
  fun resumeResource(
    @PathVariable("id") id: String,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ) {
    actuationPauser.resumeResource(id, user)
  }

  @PostMapping(
    path = ["/diff"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("@authorizationSupport.hasApplicationPermission('READ', 'APPLICATION', #resource.spec.application)")
  fun diff(
    @RequestBody resource: SubmittedResource<*>
  ): DiffResult {
    log.debug("Diffing: $resource")
    return runBlocking { adHocDiffer.calculate(resource) }
  }
}
