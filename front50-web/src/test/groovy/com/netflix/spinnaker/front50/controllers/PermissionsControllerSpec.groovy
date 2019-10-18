/*
 * Copyright 2019 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.front50.controllers

import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.fiat.shared.FiatService
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO
import spock.lang.Specification
import spock.lang.Unroll

public class PermissionsControllerSpec extends Specification {

  @Unroll
  def "test application creation will sync roles in fiat"(permission, expectedSyncedRoles) {
    given:
    def fiatService = Mock(FiatService)
    PermissionsController permissionsController = createPermissionController(fiatService)
    permissionsController.applicationPermissionDAO = Optional.of(Mock(ApplicationPermissionDAO) {
      create(_, _) >> permission
    })

    when:
    permissionsController.createApplicationPermission(permission)

    then:
    1 * fiatService.sync(expectedSyncedRoles)

    where:
    permission                                                  | expectedSyncedRoles
    appPermission(null)                                         | []
    appPermission(Permissions.EMPTY)                            | []
    appPermission(permissions(Authorization.WRITE, "my_group")) | ["my_group"]
  }

  private Application.Permission appPermission(Permissions permissions) {
    def permission = new Application.Permission()
    permission.name = "testName"
    permission.permissions = permissions
    permission
  }

  private static Permissions permissions(Authorization authorization, String group) {
    new Permissions.Builder()
      .add(authorization, group)
      .build()
  }

  private PermissionsController createPermissionController(FiatService fiatService) {
    def permissionsController = new PermissionsController()
    permissionsController.roleSync = true
    permissionsController.fiatService = fiatService
    permissionsController.fiatClientConfigurationProperties = Mock(FiatClientConfigurationProperties) {
      isEnabled() >> true
    }
    permissionsController
  }
}
