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

import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.fiat.providers.ApplicationProvider
import com.netflix.spinnaker.fiat.providers.DefaultAccountProvider
import com.netflix.spinnaker.fiat.providers.DefaultServiceAccountProvider
import com.netflix.spinnaker.fiat.providers.ResourceProvider
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService
import com.netflix.spinnaker.fiat.providers.internal.Front50Service
import com.netflix.spinnaker.fiat.roles.UserRolesProvider
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DefaultPermissionsResolverSpec extends Specification {

  @Shared
  Account noReqGroupsAcct = new Account().setName("noReqGroups")
  @Shared
  Account reqGroup1Acct = new Account().setName("reqGroup1")
                                       .setRequiredGroupMembership(["group1"])
  @Shared
  Account reqGroup1and2Acct = new Account().setName("reqGroup1and2")
                                           .setRequiredGroupMembership(["group1", "group2"])

  @Shared
  ClouddriverService clouddriverService = Mock(ClouddriverService) {
    getAccounts() >> [noReqGroupsAcct, reqGroup1Acct, reqGroup1and2Acct]
  }

  @Shared
  DefaultAccountProvider accountProvider = new DefaultAccountProvider(
      clouddriverService: clouddriverService
  )

  @Shared ServiceAccount group1SvcAcct = new ServiceAccount().setName("group1")
  @Shared ServiceAccount group2SvcAcct = new ServiceAccount().setName("group2@domain.com")

  @Shared
  Front50Service front50Service = Mock(Front50Service) {
    getAllServiceAccounts() >> [group1SvcAcct, group2SvcAcct]
  }

  @Shared
  DefaultServiceAccountProvider serviceAccountProvider = new DefaultServiceAccountProvider(
      front50Service: front50Service
  )

  @Shared
  ApplicationProvider applicationProvider = Mock(ApplicationProvider) {
    getAll(*_) >> []
  }

  @Shared
  List<ResourceProvider> resourceProviders = [accountProvider,
                                              applicationProvider,
                                              serviceAccountProvider]

  def "should resolve the anonymous user permission, when enabled"() {
    setup:
    @Subject DefaultPermissionsResolver resolver = new DefaultPermissionsResolver()
        .setResourceProviders(resourceProviders)

    when:
    def result = resolver.resolveUnrestrictedUser()

    then:
    result == new UserPermission().setId("__unrestricted_user__")
                                  .setAccounts([noReqGroupsAcct] as Set)
  }

  def "should resolve a single user's permissions"() {
    setup:
    def testUserId = "testUserId"
    UserRolesProvider userRolesProvider = Mock(UserRolesProvider)
    @Subject DefaultPermissionsResolver resolver = new DefaultPermissionsResolver()
        .setUserRolesProvider(userRolesProvider)
        .setResourceProviders(resourceProviders)

    def role1 = new Role("group1")
    def role2 = new Role("group2")

    def testUser = new ExternalUser().setId(testUserId).setExternalRoles([role1])

    when:
    resolver.resolve(null as String)

    then:
    thrown IllegalArgumentException

    when:
    def result = resolver.resolve(testUserId)

    then:
    1 * userRolesProvider.loadRoles(testUserId) >> []
    def expected = new UserPermission().setId(testUserId)
    result == expected

    when:
    result = resolver.resolve(testUserId)

    then:
    1 * userRolesProvider.loadRoles(testUserId) >> [role2]
    expected.setAccounts([reqGroup1and2Acct] as Set)
            .setServiceAccounts([group2SvcAcct] as Set)
            .setRoles([role2] as Set)
    result == expected

    when: "merge externally provided roles"
    result = resolver.resolveAndMerge(testUser)

    then:
    1 * userRolesProvider.loadRoles(testUserId) >> [role2]
    expected.setAccounts([reqGroup1Acct, reqGroup1and2Acct] as Set)
            .setServiceAccounts([group1SvcAcct, group2SvcAcct] as Set)
            .setRoles([role1, role2] as Set)
    result == expected
  }

  def "should resolve all user's permissions"() {
    setup:
    UserRolesProvider userRolesProvider = Mock(UserRolesProvider)
    @Subject DefaultPermissionsResolver resolver = new DefaultPermissionsResolver()
        .setUserRolesProvider(userRolesProvider)
        .setResourceProviders(resourceProviders)

    def role1 = new Role("group1")
    def role2 = new Role("group2")

    def extUser1 = new ExternalUser().setId("user1")
    def extUser2 = new ExternalUser().setId("user2")

    when:
    resolver.resolve(null as Collection)

    then:
    thrown IllegalArgumentException

    when:
    1 * userRolesProvider.multiLoadRoles(_) >> [
        user1: [role1],
        user2: [role2],
    ]
    def result = resolver.resolve([extUser1, extUser2])

    then:
    def user1 = new UserPermission().setId("user1")
                                    .setAccounts([reqGroup1Acct, reqGroup1and2Acct] as Set)
                                    .setServiceAccounts([group1SvcAcct] as Set)
                                    .setRoles([role1] as Set)
    def user2 = new UserPermission().setId("user2")
                                    .setAccounts([reqGroup1and2Acct] as Set)
                                    .setServiceAccounts([group2SvcAcct] as Set)
                                    .setRoles([role2] as Set)
    result == ["user1": user1, "user2": user2]

    when:
    def extRole = new Role("extRole").setSource(Role.Source.EXTERNAL)
    def extUser3 = new ExternalUser().setId("user3").setExternalRoles([extRole])
    1 * userRolesProvider.multiLoadRoles(_) >> [
        "user3": [role1]
    ]
    result = resolver.resolve([extUser3])

    then:
    def user3 = new UserPermission().setId("user3")
                                    .setAccounts([reqGroup1Acct, reqGroup1and2Acct] as Set)
                                    .setServiceAccounts([group1SvcAcct] as Set)
                                    .setRoles([role1, extRole] as Set)
    result == ["user3": user3]
  }
}
