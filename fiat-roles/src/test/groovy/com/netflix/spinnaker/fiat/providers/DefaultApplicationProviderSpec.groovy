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

  @Subject DefaultApplicationProvider provider

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

    provider = new DefaultApplicationProvider(front50Service, clouddriverService, allowAccessToUnknownApplications)

    when:
    def restrictedResult = provider.getAllRestricted([new Role(role)] as Set<Role>, false)
    List<String> restrictedApplicationNames = restrictedResult*.name

    def unrestrictedResult = provider.getAllUnrestricted()
    List<String> unrestrictedApplicationNames = unrestrictedResult*.name

    then:
    CollectionUtils.disjunction(
        new HashSet(restrictedApplicationNames + unrestrictedApplicationNames),
        expectedApplicatioNames
    ).isEmpty()

    where:
    allowAccessToUnknownApplications | role       || expectedApplicatioNames
    false                            | "role"     || ["onlyKnownToFront50", "app1", "onlyKnownToClouddriver"]
    false                            | "bad_role" || ["onlyKnownToFront50", "onlyKnownToClouddriver"]
    true                             | "role"     || ["app1"]
    true                             | "bad_role" || ["app1"]

    action = allowAccessToUnknownApplications ? "exclude" : "include"
  }
}
