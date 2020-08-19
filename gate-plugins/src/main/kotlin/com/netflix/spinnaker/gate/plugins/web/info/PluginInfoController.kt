/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 */
package com.netflix.spinnaker.gate.plugins.web.info

import com.netflix.spinnaker.gate.plugins.web.PluginService
import com.netflix.spinnaker.gate.plugins.web.SpinnakerExtensionsConfigProperties
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.kork.plugins.update.internal.SpinnakerPluginInfo
import io.swagger.annotations.ApiOperation
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * EndPoints supporting CRUD operations for PluginInfo objects.
 */
@RestController
@RequestMapping("/plugins/info")
class PluginInfoController(
  private val pluginService: PluginService,
  private val front50Service: Front50Service,
  private val spinnakerExtensionsConfigProperties: SpinnakerExtensionsConfigProperties
) {

  @ApiOperation(value = "Persist plugin metadata information")
  @RequestMapping(
    method = [RequestMethod.POST, RequestMethod.PUT],
    consumes = [MediaType.APPLICATION_JSON_VALUE]
  )
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  fun persistPluginInfo(@RequestBody pluginInfo: SpinnakerPluginInfo): Map<String, Any> {
    return pluginService.upsertPluginInfo(pluginInfo)
  }

  @ApiOperation(value = "Delete plugin info with the provided Id")
  @PreAuthorize("hasPermission(#this.this.appName, 'APPLICATION', 'WRITE')")
  @RequestMapping(
    value = ["/{id:.+}"],
    method = [RequestMethod.DELETE],
    consumes = [MediaType.APPLICATION_JSON_VALUE]
  )
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  fun deletePluginInfo(@PathVariable id: String): Map<String, Any> {
    return pluginService.deletePluginInfo(id)
  }

  @ApiOperation(value = "Get all plugin info objects")
  @RequestMapping(method = [RequestMethod.GET])
  fun getAllPluginInfo(@RequestParam(value = "service", required = false) service: String?): List<*> {
    return front50Service.getPluginInfo(service)
  }

  val appName: String
    get() { return spinnakerExtensionsConfigProperties.applicationName }
}
