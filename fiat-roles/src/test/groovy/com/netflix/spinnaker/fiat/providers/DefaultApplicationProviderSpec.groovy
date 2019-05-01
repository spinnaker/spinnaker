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

package com.netflix.spinnaker.fiat.providers

import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService
import com.netflix.spinnaker.fiat.providers.internal.Front50Service
import org.apache.commons.collections4.CollectionUtils
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DefaultApplicationProviderSpec extends Specification {
  private static final Authorization R = Authorization.READ
  private static final Authorization W = Authorization.WRITE
  private static final Authorization E = Authorization.EXECUTE

  ClouddriverService clouddriverService = Mock(ClouddriverService)
  Front50Service front50Service = Mock(Front50Service)

  @Subject DefaultApplicationProvider provider

  def makePerms(Map<Authorization, List<String>> auths) {
    return Permissions.Builder.factory(auths).build()
  }

  @Unroll
  def "should #action applications with empty permissions when allowAccessToUnknownApplications = #allowAccessToUnknownApplications"() {
    setup:
    Front50Service front50Service = Mock(Front50Service) {
      getAllApplicationPermissions() >> [
          new Application().setName("onlyKnownToFront50"),
          new Application().setName("app1")
                           .setPermissions(new Permissions.Builder().add(Authorization.READ, "role").build()),
      ]

    }
    ClouddriverService clouddriverService = Mock(ClouddriverService) {
      getApplications() >> [
          new Application().setName("app1"),
          new Application().setName("onlyKnownToClouddriver")
      ]
    }

    provider = new DefaultApplicationProvider(front50Service, clouddriverService, allowAccessToUnknownApplications, Authorization.READ)

    when:
    def restrictedResult = provider.getAllRestricted([new Role(role)] as Set<Role>, false)
    List<String> restrictedApplicationNames = restrictedResult*.name

    def unrestrictedResult = provider.getAllUnrestricted()
    List<String> unrestrictedApplicationNames = unrestrictedResult*.name

    then:
    CollectionUtils.disjunction(
        new HashSet(restrictedApplicationNames + unrestrictedApplicationNames),
        expectedApplicationNames
    ).isEmpty()

    where:
    allowAccessToUnknownApplications | role       || expectedApplicationNames
    false                            | "role"     || ["onlyKnownToFront50", "app1", "onlyKnownToClouddriver"]
    false                            | "bad_role" || ["onlyKnownToFront50", "onlyKnownToClouddriver"]
    true                             | "role"     || ["app1"]
    true                             | "bad_role" || ["app1"]

    action = allowAccessToUnknownApplications ? "exclude" : "include"
  }

  @Unroll
  def "should add fallback execute permissions only to applications where it is not already set"() {
    setup:
    def app = new Application().setName("app")

    when:
    app.setPermissions(makePerms(givenPermissions))
    provider = new DefaultApplicationProvider(
        front50Service, clouddriverService, allowAccessToUnknownApplications, Authorization.READ
    )
    def resultApps = provider.getAll()

    then:
    1 * front50Service.getAllApplicationPermissions() >> [app]
    1 * clouddriverService.getApplications() >> []

    resultApps.size() == 1
    makePerms(expectedPermissions) == resultApps.permissions[0]

    where:
    givenPermissions           | allowAccessToUnknownApplications || expectedPermissions
    [:]                        | false                            || [:]
    [(R): ['r']]               | false                            || [(R): ['r'], (W): [], (E): ['r']]
    [(R): ['r'], (E): ['foo']] | false                            || [(R): ['r'], (W): [], (E): ['foo']]
    [(R): ['r']]               | true                             || [(R): ['r'], (W): [], (E): ['r']]
  }

  @Unroll
  def "should add fallback execute permissions based on executeFallback value" () {
    setup:
    def app = new Application().setName("app")

    when:
    app.setPermissions(makePerms(givenPermissions))
    provider = new DefaultApplicationProvider(front50Service, clouddriverService, false, fallback)
    def resultApps = provider.getAll()

    then:
    1 * front50Service.getAllApplicationPermissions() >> [app]
    1 * clouddriverService.getApplications() >> []

    resultApps.size() == 1
    makePerms(expectedPermissions) == resultApps.permissions[0]

    where:
    fallback    || givenPermissions         || expectedPermissions
    R           || [(R): ['r']]             || [(R): ['r'], (W): [], (E): ['r']]
    W           || [(R): ['r'], (W): ['w']] || [(R): ['r'], (W): ['w'], (E): ['w']]
  }
}
