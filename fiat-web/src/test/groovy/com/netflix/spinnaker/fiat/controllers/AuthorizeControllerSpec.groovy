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
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository
import spock.lang.Specification

class AuthorizeControllerSpec extends Specification {

  def "should get user from repo"() {
    setup:
    PermissionsRepository repository = Mock(PermissionsRepository)
    AuthorizeController controller = new AuthorizeController(permissionsRepository: repository)
    def foo = new UserPermission().setId("foo@batman.com")

    when:
    controller.getUserPermission("foo%40batman.com")

    then:
    1 * repository.get("foo@batman.com") >> Optional.empty()
    thrown NotFoundException

    when:
    def result = controller.getUserPermission("foo%40batman.com")

    then:
    1 * repository.get("foo@batman.com") >> Optional.of(foo)
    result == foo.view
  }

  def "should get user's accounts from repo"() {
    setup:
    PermissionsRepository repository = Mock(PermissionsRepository)
    AuthorizeController controller = new AuthorizeController(permissionsRepository: repository)
    def bar = new Account().setName("bar")
    def foo = new UserPermission().setId("foo").setAccounts([bar] as Set)

    when:
    controller.getUserAccounts("foo")

    then:
    1 * repository.get("foo") >> Optional.empty()
    thrown NotFoundException

    when:
    def result = controller.getUserAccounts("foo")

    then:
    1 * repository.get("foo") >> Optional.of(foo)
    result == [bar.view] as Set

    when:
    result = controller.getUserAccount("foo", "bar")

    then:
    1 * repository.get("foo") >> Optional.of(foo)
    result == bar.view
  }
}
