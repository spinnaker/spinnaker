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

import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/permissions")
@ConditionalOnExpression('${spinnaker.gcs.enabled:false} || ${spinnaker.s3.enabled:false}')
public class PermissionsController {

  @Autowired
  ApplicationPermissionDAO applicationPermissionDAO;

  @ApiOperation(value = "", notes = "Get all application permissions. Internal use only.")
  @RequestMapping(method = RequestMethod.GET, value = "/applications")
  Set<Application.Permission> getAllApplicationPermissions() {
    applicationPermissionDAO.all();
  }

  @ApiOperation(value = "", notes = "Create an application permission.")
  @RequestMapping(method = RequestMethod.POST, value = "/applications")
  Application.Permission createApplicationPermission(
      @RequestBody Application.Permission permission) {
    return applicationPermissionDAO.create(permission.id, permission)
  }

  @RequestMapping(method = RequestMethod.PUT, value = "/applications/{appName:.+}")
  Application.Permission updateApplicationPermission(
      @PathVariable String appName,
      @RequestBody Application.Permission permission) {
    applicationPermissionDAO.update(appName, permission)
    return applicationPermissionDAO.findById(appName);
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/applications/{appName:.+}")
  void deleteApplicationPermission(@PathVariable String appName) {
    applicationPermissionDAO.delete(appName);
  }
}
