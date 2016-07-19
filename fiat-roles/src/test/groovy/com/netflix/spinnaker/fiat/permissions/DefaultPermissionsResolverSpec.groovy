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

package com.netflix.spinnaker.fiat.permissions

import com.netflix.spinnaker.fiat.model.ServiceAccount
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.providers.ApplicationProvider
import com.netflix.spinnaker.fiat.providers.DefaultAccountProvider
import com.netflix.spinnaker.fiat.providers.ServiceAccountProvider
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService
import com.netflix.spinnaker.fiat.roles.UserRolesProvider
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DefaultPermissionsResolverSpec extends Specification {

  @Shared
  ClouddriverService clouddriverService = Mock(ClouddriverService) {
    getAccounts() >> [
        new Account().setName("noReqGroups"),
        new Account().setName("reqGroup1").setRequiredGroupMembership(["group1"]),
        new Account().setName("reqGroup1and2").setRequiredGroupMembership(["group1", "group2"]),
    ]
  }

  @Shared
  DefaultAccountProvider accountProvider = new DefaultAccountProvider(
      clouddriverService: clouddriverService
  )

  @Shared
  ServiceAccountProvider serviceAccountProvider = new ServiceAccountProvider().setServiceAccountsByName(
      [
          "group1": new ServiceAccount().setName("group1"),
          "group2": new ServiceAccount().setName("group2"),
      ]
  )

  def "should resolve a single user's permissions"() {
    setup:
    def testUserId = "testUserId"
    UserRolesProvider userRolesProvider = Mock(UserRolesProvider)
    ApplicationProvider applicationProvider = Mock(ApplicationProvider) {
      getApplications(*_) >> []
    }
    @Subject DefaultPermissionsResolver resolver = new DefaultPermissionsResolver()
        .setUserRolesProvider(userRolesProvider)
        .setAccountProvider(accountProvider)
        .setApplicationProvider(applicationProvider)
        .setServiceAccountProvider(serviceAccountProvider)

    when:
    resolver.resolve(null as String)

    then:
    thrown IllegalArgumentException

    when:
    def result = resolver.resolve(testUserId)

    then:
    1 * userRolesProvider.loadRoles(testUserId) >> []
    result?.getId() == testUserId
    result?.getAccounts()?.size() == 1
    result?.getAccounts()*.name.containsAll(["noReqGroups"])
    result?.getApplications() == [] as Set
    result?.getServiceAccounts()?.size() == 0

    when:
    result = resolver.resolve(testUserId)

    then:
    1 * userRolesProvider.loadRoles(testUserId) >> ["group2"]
    result?.getAccounts()?.size() == 2
    result?.getAccounts()*.name.containsAll(["noReqGroups", "reqGroup1and2"])
    result?.getServiceAccounts()?.size() == 1
    result?.getServiceAccounts()*.name.containsAll(["group2"])

    when: "different capitalization"
    result = resolver.resolve(testUserId)

    then:
    1 * userRolesProvider.loadRoles(testUserId) >> ["gRoUp2"]
    result?.getAccounts()?.size() == 2
    result?.getAccounts()*.name.containsAll(["noReqGroups", "reqGroup1and2"])
    result?.getServiceAccounts()?.size() == 1
    result?.getServiceAccounts()*.name.containsAll(["group2"])

    when: "merge externally provided roles"
    result = resolver.resolveAndMerge(testUserId, ["group1"])

    then:
    1 * userRolesProvider.loadRoles(testUserId) >> ["group2"]
    result?.getAccounts()?.size() == 3
    result?.getAccounts()*.name.containsAll(["noReqGroups", "reqGroup1", "reqGroup1and2"])
    result?.getServiceAccounts()?.size() == 2
    result?.getServiceAccounts()*.name.containsAll(["group1", "group2"])
  }

  def "should resolve all user's permissions"() {
    setup:
    def user1 = "user1"
    def user2 = "user2"
    UserRolesProvider userRolesProvider = Mock(UserRolesProvider)
    ApplicationProvider applicationProvider = Mock(ApplicationProvider) {
      getApplications(*_) >> []
    }
    @Subject DefaultPermissionsResolver resolver = new DefaultPermissionsResolver()
        .setUserRolesProvider(userRolesProvider)
        .setAccountProvider(accountProvider)
        .setApplicationProvider(applicationProvider)
        .setServiceAccountProvider(serviceAccountProvider)

    when:
    resolver.resolve(null as Collection)

    then:
    thrown IllegalArgumentException

    when:
    1 * userRolesProvider.multiLoadRoles(_) >> [
        user1: ["group1"],
        user2: ["group2"],
    ]
    def result = resolver.resolve([user1, user2])

    then:
    result.size() == 2
    result["user1"]?.id == "user1"
    result["user1"]?.getAccounts()*.name.containsAll(["noReqGroups", "reqGroup1", "reqGroup1and2"])
    result["user1"]?.getApplications() == [] as Set
    result["user1"]?.getServiceAccounts()*.name.containsAll(["group1"])
    result["user2"]?.id == "user2"
    result["user2"]?.getAccounts()*.name.containsAll(["noReqGroups", "reqGroup1and2"])
    result["user2"]?.getApplications() == [] as Set
    result["user2"]?.getServiceAccounts()*.name.containsAll(["group2"])
  }
}
