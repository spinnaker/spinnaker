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

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.fiat.shared.FiatService
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO
import groovy.util.logging.Slf4j
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.web.bind.annotation.*
import retrofit.RetrofitError

@Slf4j
@RestController
@RequestMapping("/permissions")
@ConditionalOnExpression('${spinnaker.gcs.enabled:false} || ${spinnaker.s3.enabled:false} || ${spinnaker.azs.enabled:false} || ${spinnaker.oracle.enabled:false}')
public class PermissionsController {

  @Autowired
  ApplicationPermissionDAO applicationPermissionDAO

  @Autowired
  ApplicationDAO applicationDAO

  @Autowired(required = false)
  FiatService fiatService

  @Autowired
  FiatClientConfigurationProperties fiatClientConfigurationProperties

  @Value('${fiat.role-sync.enabled:true}')
  Boolean roleSync

  @ApiOperation(value = "", notes = "Get all application permissions. Internal use only.")
  @RequestMapping(method = RequestMethod.GET, value = "/applications")
  Set<Application.Permission> getAllApplicationPermissions() {
    Map<String, Application.Permission> actualPermissions = applicationPermissionDAO
        .all()
        .collectEntries { Application.Permission perm ->
      return [(perm.name.toLowerCase()): perm]
    }

    applicationDAO.all().each {
      if (!actualPermissions.containsKey(it.name.toLowerCase())) {
        actualPermissions.put(it.name.toLowerCase(),
                              new Application.Permission(name: it.name,
                                                         lastModified: -1,
                                                         lastModifiedBy: "auto-generated"))
      }
    }

    return actualPermissions.values()
  }

  @RequestMapping(method = RequestMethod.GET, value = "/applications/{appName:.+}")
  Application.Permission getApplicationPermission(@PathVariable String appName) {
      return applicationPermissionDAO.findById(appName)
  }

  @ApiOperation(value = "", notes = "Create an application permission.")
  @RequestMapping(method = RequestMethod.POST, value = "/applications")
  Application.Permission createApplicationPermission(
      @RequestBody Application.Permission newPermission) {
    def perm = applicationPermissionDAO.create(newPermission.id, newPermission)
    syncUsers(perm, null)
    return perm
  }

  @RequestMapping(method = RequestMethod.PUT, value = "/applications/{appName:.+}")
  Application.Permission updateApplicationPermission(
      @PathVariable String appName,
      @RequestBody Application.Permission newPermission) {
    try {
      def oldPermission = applicationPermissionDAO.findById(appName)
      applicationPermissionDAO.update(appName, newPermission)
      syncUsers(newPermission, oldPermission)
    } catch (NotFoundException nfe) {
      createApplicationPermission(newPermission)
    }
    return newPermission
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/applications/{appName:.+}")
  void deleteApplicationPermission(@PathVariable String appName) {
    def oldPermission = applicationPermissionDAO.findById(appName)
    applicationPermissionDAO.delete(appName)
    syncUsers(null, oldPermission)
  }

  private void syncUsers(Application.Permission newPermission, Application.Permission oldPermission) {
    if (!fiatClientConfigurationProperties.enabled || !fiatService) {
      return
    }

    // Specifically using an empty list here instead of null, because an empty list will update
    // the anonymous user's app list.
    Set<String> roles = []

    if (newPermission?.permissions?.isRestricted()) {
      roles += newPermission.permissions.allGroups()
    }

    if (oldPermission?.permissions?.isRestricted()) {
      roles += oldPermission.permissions.allGroups()
    }

    if (!roles.isEmpty() && roleSync) {
      try {
        fiatService.sync(roles as List)
      } catch (RetrofitError re) {
        log.warn("Error syncing users", re)
      }
    }
  }
}
