/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.controllers

import com.netflix.spinnaker.front50.ApplicationPermissionsService
import com.netflix.spinnaker.front50.model.application.Application
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/permissions")
public class PermissionsController {

  ApplicationPermissionsService permissionsService

  PermissionsController(ApplicationPermissionsService permissionsService) {
    this.permissionsService = permissionsService
  }

  @ApiOperation(value = "", notes = "Get all application permissions. Internal use only.")
  @RequestMapping(method = RequestMethod.GET, value = "/applications")
  Set<Application.Permission> getAllApplicationPermissions() {
    return permissionsService.getAllApplicationPermissions()
  }

  @RequestMapping(method = RequestMethod.GET, value = "/applications/{appName:.+}")
  Application.Permission getApplicationPermission(@PathVariable String appName) {
    return permissionsService.getApplicationPermission(appName)
  }

  @ApiOperation(value = "", notes = "Create an application permission.")
  @RequestMapping(method = RequestMethod.POST, value = "/applications")
  Application.Permission createApplicationPermission(
      @RequestBody Application.Permission newPermission) {
    return permissionsService.createApplicationPermission(newPermission)
  }

  @RequestMapping(method = RequestMethod.PUT, value = "/applications/{appName:.+}")
  Application.Permission updateApplicationPermission(
      @PathVariable String appName,
      @RequestBody Application.Permission newPermission) {
    return permissionsService.updateApplicationPermission(appName, newPermission, false)
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/applications/{appName:.+}")
  void deleteApplicationPermission(@PathVariable String appName) {
    permissionsService.deleteApplicationPermission(appName)
  }
}
