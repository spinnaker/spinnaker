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
 */
package com.netflix.spinnaker.front50

import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.security.authz.Authorization
import com.netflix.spinnaker.security.authz.Permissions
import com.netflix.spinnaker.security.authz.config.ApplicationDefaultPermissionsProperties
import spock.lang.Specification
import spock.lang.Unroll

class ApplicationPermissionsServiceSpec extends Specification {

  @Unroll
  def "creating an application permission persists it to the owner-local permission store"(permission) {
    given:
    def permissionDAO = Mock(ApplicationPermissionDAO)
    ApplicationPermissionsService subject = createSubject(permissionDAO)

    when:
    subject.createApplicationPermission(permission)

    then:
    // Owner-local enforcement: Front50 persists the ACL to its own store and does NOT
    // call out to any external permission service to sync roles.
    1 * permissionDAO.create(permission.getId(), _) >> permission
    0 * _

    where:
    permission << [
      appPermission(null),
      appPermission(Permissions.EMPTY),
      appPermission(permissions(Authorization.WRITE, "my_group")),
    ]
  }

  def "a write stores only the application's own grants, never the global defaults"() {
    given: "an install with a global default READ grant"
    def permissionDAO = Mock(ApplicationPermissionDAO)
    def subject = createSubject(permissionDAO, defaults(Authorization.READ, "spin-internal-service-accounts"))

    and: "a client that read the effective ACL and submitted the whole list back unchanged"
    def submitted = appPermission(new Permissions.Builder()
      .add(Authorization.READ, "team-a")
      .add(Authorization.READ, "spin-internal-service-accounts")
      .build())

    when:
    subject.updateApplicationPermission("testName", submitted, true)

    then: "the default is not baked into the application's own record, where it would outlive being un-defaulted"
    1 * permissionDAO.findById("testName") >> submitted
    1 * permissionDAO.update("testName", { it.permissions.get(Authorization.READ) == ["team-a"] as Set })
  }

  def "creating a permission strips the defaults the same way"() {
    given:
    def permissionDAO = Mock(ApplicationPermissionDAO)
    def subject = createSubject(permissionDAO, defaults(Authorization.READ, "spin-internal-service-accounts"))

    when:
    subject.createApplicationPermission(appPermission(new Permissions.Builder()
      .add(Authorization.READ, "team-a")
      .add(Authorization.READ, "spin-internal-service-accounts")
      .build()))

    then:
    1 * permissionDAO.create("testname", { it.permissions.get(Authorization.READ) == ["team-a"] as Set })
  }

  def "reports the configured global defaults on their own"() {
    given: "the application creation form has no record to read the defaults from"
    def subject = createSubject(Mock(ApplicationPermissionDAO), defaults(Authorization.READ, "everyone"))

    expect:
    subject.getDefaultApplicationPermissions().get(Authorization.READ) == ["everyone"] as Set
  }

  def "reports an empty ACL when no defaults are configured"() {
    given:
    def subject = createSubject(Mock(ApplicationPermissionDAO))

    expect:
    !subject.getDefaultApplicationPermissions().isRestricted()
  }

  def "reading the effective ACL merges the defaults, reading the record does not"() {
    given:
    def permissionDAO = Mock(ApplicationPermissionDAO)
    def subject = createSubject(permissionDAO, defaults(Authorization.READ, "spin-internal-service-accounts"))
    permissionDAO.findById("testName") >> appPermission(permissions(Authorization.READ, "team-a"))

    expect: "the effective ACL is what an authorization decision is made against"
    subject.getApplicationPermission("testName", true).permissions.get(Authorization.READ) ==
      ["team-a", "spin-internal-service-accounts"] as Set

    and: "the record itself stays free of the defaults, so editing it cannot persist them"
    subject.getApplicationPermission("testName").permissions.get(Authorization.READ) == ["team-a"] as Set
  }

  def "an application with no record of its own has the defaults as its effective ACL"() {
    given:
    def permissionDAO = Mock(ApplicationPermissionDAO)
    def subject = createSubject(permissionDAO, defaults(Authorization.READ, "spin-internal-service-accounts"))
    permissionDAO.findById("ghost") >> { throw new NotFoundException("no permission record") }

    expect:
    subject.getApplicationPermission("ghost", true).permissions.get(Authorization.READ) ==
      ["spin-internal-service-accounts"] as Set
  }

  def "with no defaults configured, an application with no record is still not found"() {
    given: "nothing to answer with, so the caller's unknown-application handling must decide"
    def permissionDAO = Mock(ApplicationPermissionDAO)
    def subject = createSubject(permissionDAO)
    permissionDAO.findById("ghost") >> { throw new NotFoundException("no permission record") }

    when:
    subject.getApplicationPermission("ghost", true)

    then:
    thrown(NotFoundException)
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

  private static ApplicationDefaultPermissionsProperties defaults(Authorization authorization, String group) {
    def properties = new ApplicationDefaultPermissionsProperties()
    properties.setDefaultPermissions([(authorization): [group] as Set])
    properties
  }

  private ApplicationPermissionsService createSubject(
    ApplicationPermissionDAO applicationPermissionDAO,
    ApplicationDefaultPermissionsProperties applicationDefaultPermissions = new ApplicationDefaultPermissionsProperties()) {
    return new ApplicationPermissionsService(
      Mock(ApplicationDAO),
      Optional.of(applicationPermissionDAO),
      [],
      applicationDefaultPermissions
    )
  }
}
