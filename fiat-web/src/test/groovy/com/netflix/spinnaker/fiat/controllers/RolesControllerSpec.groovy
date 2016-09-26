/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.fiat.controllers

import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.permissions.PermissionResolutionException
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository
import com.netflix.spinnaker.fiat.permissions.PermissionsResolver
import spock.lang.Specification
import spock.lang.Subject

class RolesControllerSpec extends Specification {

  def "should put user in repo or throw error"() {
    setup:
    def user = new UserPermission().setId("user")
    PermissionsRepository repo = Mock(PermissionsRepository)
    PermissionsResolver resolver = Mock(PermissionsResolver) {
      resolve("empty") >> { throw new PermissionResolutionException()}
      resolve("user") >> user
    }
    @Subject RolesController controller = new RolesController(permissionsResolver: resolver,
                                                              permissionsRepository: repo)

    when:
    controller.putUserPermission("empty")

    then:
    thrown UserPermissionModificationException

    when:
    controller.putUserPermission("user")

    then:
    1 * repo.put(user)
  }
}
