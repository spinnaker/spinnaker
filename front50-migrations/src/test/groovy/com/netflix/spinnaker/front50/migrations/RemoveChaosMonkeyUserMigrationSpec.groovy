package com.netflix.spinnaker.front50.migrations

import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.front50.ApplicationPermissionsService
import com.netflix.spinnaker.front50.config.ChaosMonkeyEventListenerConfigurationProperties
import com.netflix.spinnaker.front50.model.application.Application
import spock.lang.Specification
import spock.lang.Subject

class RemoveChaosMonkeyUserMigrationSpec extends Specification {

  def applicationPermissionsService = Mock(ApplicationPermissionsService)
  ChaosMonkeyEventListenerConfigurationProperties properties = new ChaosMonkeyEventListenerConfigurationProperties()

  @Subject
  def migration = new RemoveChaosMonkeyUserMigration(applicationPermissionsService, properties)

  def "does a thing"() {
    given:
    properties.userRole = "chaosmonkey@example.com"
    properties.enabled = false

    Application.Permission permissions = new Application.Permission(
      name: "hello",
      lastModifiedBy: "bird person",
      lastModified: -1L,
      permissions: new Permissions.Builder()
        .add(Authorization.READ, ["chaosmonkey@example.com", "user@example.com"])
        .add(Authorization.WRITE, ["chaosmonkey@example.com", "user@example.com"])
        .build()
    )

    Application.Permission updatedPermissions = new Application.Permission(
      name: "hello",
      lastModifiedBy: "bird person",
      lastModified: -1L,
      permissions: new Permissions.Builder()
        .add(Authorization.READ, ["user@example.com"])
        .add(Authorization.WRITE, ["user@example.com"])
        .build()
    )

    Application.Permission noChaosMonkeyPermissions = new Application.Permission(
      name: "world",
      lastModifiedBy: "bird person",
      lastModified: -1L,
      permissions: new Permissions.Builder()
        .add(Authorization.READ, ["user@example.com"])
        .add(Authorization.WRITE, ["user@example.com"])
        .build()
    )

    Application.Permission unrestrictedPermissions = new Application.Permission(
      name: "yay",
      lastModifiedBy: "bird person",
      lastModified: -1L,
      permissions: Permissions.EMPTY
    )

    when:
    migration.run()

    then:
    1 * applicationPermissionsService.getAllApplicationPermissions() >> [permissions, noChaosMonkeyPermissions, unrestrictedPermissions]
    1 * applicationPermissionsService.updateApplicationPermission(permissions.name, _ as Application.Permission, true) >> updatedPermissions
    0 * applicationPermissionsService.updateApplicationPermission(noChaosMonkeyPermissions.name, _ as Application.Permission, true) >> noChaosMonkeyPermissions
    0 * applicationPermissionsService.updateApplicationPermission(unrestrictedPermissions.name, _ as Application.Permission, true) >> unrestrictedPermissions
    permissions.getPermissions() == updatedPermissions.getPermissions()
  }
}
