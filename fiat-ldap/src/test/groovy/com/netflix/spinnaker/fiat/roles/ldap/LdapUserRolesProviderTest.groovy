/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.fiat.roles.ldap

import com.netflix.spinnaker.fiat.config.LdapConfig
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.permissions.ExternalUser
import org.apache.commons.lang3.tuple.Pair
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.ldap.control.PagedResultsDirContextProcessor
import org.springframework.security.ldap.SpringSecurityLdapTemplate
import spock.lang.Specification
import spock.lang.Unroll

import javax.naming.directory.SearchControls

class LdapUserRolesProviderTest extends Specification {

  @Unroll
  void "loadRoles should return no roles for serviceAccounts when userSearchFilter present"() {
    given:
    def id = 'foo'
    def user = new ExternalUser(id: id, externalRoles: [new Role(name: 'bar')])

    def configProps = baseConfigProps()
    configProps.groupSearchBase = groupSearchBase
    configProps.userSearchFilter = "notEmpty"

    def provider = Spy(LdapUserRolesProvider){
      1 * setConfigProps(configProps)
      1 * setLdapTemplate(_ as SpringSecurityLdapTemplate)
      1 * loadRoles(user)
      1 * loadRolesForUser(user)
      (0..2) * getPartialUserDn(id) >> "notEmpty"
      (0..1) * getUserDNs([id])
      (0..1) * getUserFullDn(id) >> null
      0 * _
    }
    provider.configProps = configProps
    provider.ldapTemplate = Mock(SpringSecurityLdapTemplate) {
      (0..1) * searchForSingleEntry(*_) >> { throw new IncorrectResultSizeDataAccessException(1) }
      (0..1) * search(*_)
      0 * _
    }

    when:
    def roles = provider.loadRoles(user)

    then:
    roles == Collections.emptyList()

    where:
    groupSearchBase|_
    ""             |_
    "notEmpty"     |_

  }

  @Unroll
  void "loadRoles should return no roles for serviceAccouts when userSearchFilter absent"() {
    given:
    def id = 'id'
    def user = new ExternalUser(id: id, externalRoles: [new Role(name: 'bar')])

    def configProps = baseConfigProps()
    configProps.groupSearchBase = groupSearchBase

    def provider = Spy(LdapUserRolesProvider){
      1 * setConfigProps(configProps)
      1 * setLdapTemplate(_ as SpringSecurityLdapTemplate)
      1 * loadRoles(user)
      1 * loadRolesForUser(user)
      (0..2) * getPartialUserDn(id) >> "notEmpty"
      (0..1) * getUserDNs([id])
      (0..1) * getUserFullDn(id) >> null
      0 * _
    }

    provider.configProps = configProps
    provider.ldapTemplate = Mock(SpringSecurityLdapTemplate) {
      (0..1) * searchForSingleEntry(*_) >> { throw new IncorrectResultSizeDataAccessException(1) }
      (0..1) * searchForSingleAttributeValues(*_) >> new HashSet<>()
      0 * _
    }

    when:
    def roles = provider.loadRoles(user)

    then:
    roles == Collections.emptyList()

    where:
    groupSearchBase|_
    ""             |_
    "notEmpty"     |_
  }

  void "multiLoadRoles should use loadRoles when groupUserAttributes is empty"() {
    given:
    def users = [externalUser("user1"), externalUser("user2")]
    def role1 = new Role("group1")
    def role2 = new Role("group2")

    def configProps = baseConfigProps()
    def provider = Spy(LdapUserRolesProvider){
     2 * loadRoles(_ as ExternalUser) >>> [[role1], [role2]]
     1 * setConfigProps(configProps)
     0 * _
    }
    provider.setConfigProps(configProps)

    when:
    configProps.groupSearchBase = ""
    def roles = provider.multiLoadRoles(users)

    then:
    roles == [user1: [], user2: []]
    1 * provider.multiLoadRoles(users)
    1 * provider.loadRolesForUsers(users)

    when:
    configProps.groupSearchBase = "notEmpty"
    roles = provider.multiLoadRoles(users)

    then:
    roles == [user1: [role1], user2: [role2]]
    1 * provider.multiLoadRoles(users)
    1 * provider.loadRolesForUsers(users)
  }

  void "multiLoadRoles should use groupUserAttributes when groupUserAttributes is not empty"() {
    given:
    def users = [externalUser("user1"), externalUser("user2")]
    def role1 = new Role("group1")
    def role2 = new Role("group2")

    def configProps = baseConfigProps()
            .setUserSearchBase("ou=users")
            .setGroupSearchBase("ou=groups")
            .setGroupUserAttributes("member")
    def provider = Spy(LdapUserRolesProvider){
      2 * loadRoles(_ as ExternalUser) >>> [[role1], [role2]]
      1 * setConfigProps(configProps)
      0 * _
    }
    provider.setConfigProps(configProps)

    when: "thresholdToUseGroupMembership is too high"
    configProps.thresholdToUseGroupMembership = 100
    def roles = provider.multiLoadRoles(users)

    then: "should use loadRoles"
    1 * provider.multiLoadRoles(users)
    1 * provider.loadRolesForUsers(users)
    roles == [user1: [role1], user2: [role2]]

    when: "users count is greater than thresholdToUseGroupMembership and enableDnBasedMultiLoad is false"
    configProps.thresholdToUseGroupMembership = 1
    provider.ldapTemplate = Mock(SpringSecurityLdapTemplate) {
      1 * search(*_) >> [
              [Pair.of("user1", role1)],
              [Pair.of("user2", role2)],
              [Pair.of("unknown", role2)]
      ]
      0 * _
    }
    roles = provider.multiLoadRoles(users)

    then: "should compile user DNs locally and query memberships for all groups to load roles"
    roles == [user1: [role1], user2: [role2]]
    users.each {0 * provider.getUserFullDn(it.id) }
    1 * provider.setLdapTemplate(_ as SpringSecurityLdapTemplate)
    1 * provider.multiLoadRoles(users)
    1 * provider.loadRolesForUsers(users)

    when: "thresholdToUseGroupMembership is breached, userSearchFilter is empty and enableDnBasedMultiLoad is true"
    // Test to make sure that when the thresholdToUseGroupMembership is breached:
    // 1. User DNs are compiled using the provided User DN Pattern in the configs, instead of querying LDAP,
    //    when userSearchFilter is empty.
    // 2. Roles are loaded by querying the memberships for all groups and then filtering based on the provided user ids
    configProps.thresholdToUseGroupMembership = 1
    configProps.enableDnBasedMultiLoad = true
    provider.ldapTemplate = Mock(SpringSecurityLdapTemplate) {
      1 * search("ou=groups", "(uniqueMember=*)", _ as LdapUserRolesProvider.UserGroupMapper) >> [
              [Pair.of("uid=user1,ou=users,dc=springframework,dc=org", role1)],
              [Pair.of("uid=user2,ou=users,dc=springframework,dc=org", role2)],
              [Pair.of("unknown", role2)]
      ]
      0 * _
    }
    roles = provider.multiLoadRoles(users)

    then: "should compile user DNs locally and query memberships for all groups to load roles"
    roles == [user1: [role1], user2: [role2]]
    users.each {1 * provider.getUserFullDn(it.id) }
    1 * provider.multiLoadRoles(users)
    1 * provider.setLdapTemplate(_ as SpringSecurityLdapTemplate)
    1 * provider.doMultiLoadRoles(_)
    1 * provider.getUserDNs(_ as Collection<String>)
    1 * provider.loadRolesForUsers(users)
    2 * provider.getPartialUserDn(_ as String)

    when: "thresholdToUseGroupMembership is breached and userSearchFilter is set"
    // Test to make sure that when the thresholdToUseGroupMembership is breached:
    // 1. User DNs are fetched from LDAP when userSearchFilter is set
    // 2. Roles are loaded by querying the memberships for all groups and then filtering based on the provided user ids
    users = [externalUser("user1@foo.com"), externalUser("user2@foo.com")]
    configProps.setUserSearchFilter("(employeeEmail={0})")
    provider.ldapTemplate = Mock(SpringSecurityLdapTemplate) {
      1 * search("ou=users",
              "(|(employeeEmail=user1@foo.com)(employeeEmail=user2@foo.com))",
              _ as LdapUserRolesProvider.UserDNMapper) >>
              [Pair.of("uid=user1,ou=users,dc=springframework,dc=org", "user1@foo.com"),
               Pair.of("uid=user2,ou=users,dc=springframework,dc=org", "user2@foo.com")]
      1 * search("ou=groups", "(uniqueMember=*)", _ as LdapUserRolesProvider.UserGroupMapper) >> [
              [Pair.of("uid=user1,ou=users,dc=springframework,dc=org", role1)],
              [Pair.of("uid=user2,ou=users,dc=springframework,dc=org", role2)],
              [Pair.of("unknown", role2)]
      ]
    }
    roles = provider.multiLoadRoles(users)

    then: "should fetch user DNs from LDAP and query memberships for all groups to load roles"
    roles == ["user1@foo.com": [role1], "user2@foo.com": [role2]]
    1 * provider.multiLoadRoles(_ as Collection<ExternalUser>)
    1 * provider.setLdapTemplate(_ as SpringSecurityLdapTemplate)
    1 * provider.doMultiLoadRoles(_)
    1 * provider.getUserDNs(_ as Collection<String>)
    1 * provider.loadRolesForUsers(_ as Collection<ExternalUser>)
  }

  void "multiLoadRoles should use pagination when enabled"() {
    given:
    def users = (1..10).collect {externalUser("user${it}@foo.com") }
    def role1 = new Role("group1")
    def role2 = new Role("group2")

    def configProps = baseConfigProps()
    def provider = Spy(LdapUserRolesProvider){
      1 * setLdapTemplate(_ as SpringSecurityLdapTemplate)
      1 * setConfigProps(_ as LdapConfig.ConfigProps)
      0 * _
    }
    provider.setConfigProps(configProps)

    when: "pagination is enabled"
    configProps.setUserSearchBase("ou=users")
            .setGroupSearchBase("ou=groups")
            .setUserSearchFilter("(employeeEmail={0})")
            .setGroupUserAttributes("member")
            .setThresholdToUseGroupMembership(5)
            .setEnablePagingForGroupMembershipQueries(true)
            .setEnableDnBasedMultiLoad(true)
            .setLoadUserDNsBatchSize(5)
            .setPageSizeForGroupMembershipQueries(5)

    provider.ldapTemplate = Mock(SpringSecurityLdapTemplate) {
      1 * search("ou=users", _ as String, _ as LdapUserRolesProvider.UserDNMapper) >>
              users[0..4].collect {Pair.of("${it.id}@foo.com" as String, "${it.id}" as String) }
      1 * search("ou=users", _ as String, _ as LdapUserRolesProvider.UserDNMapper) >>
              users[5..9].collect {Pair.of("${it.id}@foo.com" as String, "${it.id}" as String) }

      2 * search("ou=groups",
              "(uniqueMember=*)",
              _ as SearchControls,
              _ as LdapUserRolesProvider.UserGroupMapper,
              _ as PagedResultsDirContextProcessor) >>> [
              users[0..4].collect {
                [Pair.of("${it.id}@foo.com" as String, role1),
                 Pair.of("${it.id}@foo.com" as String, role2)]
              },
              users[5..9].collect {
                [Pair.of("${it.id}@foo.com" as String, role1),
                 Pair.of("${it.id}@foo.com" as String, role2)]
              }]
      0 * _
    }
    def spiedProcessor = Spy(new PagedResultsDirContextProcessor(5, null))
    1 * provider.getPagedResultsDirContextProcessor(_) >> spiedProcessor
    2 * spiedProcessor.hasMore() >>> [true, false]

    def roles = provider.multiLoadRoles(users)

    then: "should fetch ldap roles using pagination"
    roles == users.collectEntries { ["${it.id}" as String, [role1, role2]] }
    1 * provider.multiLoadRoles(users)
    1 * provider.doMultiLoadRolesPaginated(_ as Collection<String>)
    1 * provider.loadRolesForUsers(_ as Collection<ExternalUser>)
    1 * provider.getUserDNs(_ as Collection<String>)
  }

  void "multiLoadRoles should merge roles when multiple DNs exist for a user id"(){
    given:
    def id = 'user1'
    def ids = [id] as Set
    def users = [externalUser(id)]
    def role1 = new Role("group1")
    def role2 = new Role("group2")
    def role3 = new Role("group3")

    def configProps = baseConfigProps()
    def provider = Spy(LdapUserRolesProvider){
      1 * getUserDNs(ids) >> ['uid=dn1,ou=users,dc=springframework,dc=org': id,
                              'uid=dn2,ou=users,dc=springframework,dc=org': id]
      1 * doMultiLoadRoles(_) >> ['uid=dn1,ou=users,dc=springframework,dc=org' : [role1,role2],
                                  'uid=dn2,ou=users,dc=springframework,dc=org': [role3] ]
      1 * setConfigProps(configProps)
      0 * _
    }
    provider.setConfigProps(configProps)

    when:
    configProps.groupSearchBase = "notEmpty"
    configProps.thresholdToUseGroupMembership = 0
    configProps.groupUserAttributes = "notEmpty"
    configProps.enableDnBasedMultiLoad = true
    def roles = provider.multiLoadRoles(users)

    then:
    roles == [user1: [role1, role2, role3]]
    1 * provider.multiLoadRoles(users)
    1 * provider.loadRolesForUsers(users)
  }

  @Unroll
  void "loadRolesForUser should merge roles when multiple DNs exist for a user id"() {
    given:
    def id = 'user1'
    def user = externalUser(id)
    def role1 = new Role("group1").setSource(Role.Source.LDAP)
    def role2 = new Role("group2").setSource(Role.Source.LDAP)

    def configProps = baseConfigProps()

    def provider = Spy(LdapUserRolesProvider) {
      1 * setConfigProps(configProps)
      1 * setLdapTemplate(_ as SpringSecurityLdapTemplate)
      1 * loadRolesForUser(user)
      1 * getPartialUserDn(id)
      1 * getUserDNs(_ as Collection<String>) >> [ 'uid=dn1,ou=users,dc=springframework,dc=org' : 'user1',
                                               'uid=dn2,ou=users,dc=springframework,dc=org' : 'user1']
      0 * _
    }
    provider.ldapTemplate = Mock(SpringSecurityLdapTemplate) {
      1 * searchForSingleEntry(*_) >> { throw new IncorrectResultSizeDataAccessException(1) } // due to multiple DNs
      1 * searchForSingleAttributeValues(_, _, ['uid=dn1,ou=users,dc=springframework,dc=org','user1'], _) >> ['group1']
      1 * searchForSingleAttributeValues(_, _, ['uid=dn2,ou=users,dc=springframework,dc=org','user1'], _) >> ['group2']
      0 * _
    }
    provider.setConfigProps(configProps)

    when:
    configProps.groupSearchBase = "notEmpty"
    configProps.userSearchFilter = "notEmpty"
    def roles = provider.loadRolesForUser(user)

    then:
    roles.sort() == [role1, role2].sort()
  }

  private static ExternalUser externalUser(String id) {
    return new ExternalUser().setId(id)
  }

  def baseConfigProps() {
    return new LdapConfig.ConfigProps(
        url: "ldap://monkeymachine:11389/dc=springframework,dc=org",
        managerDn: "manager",
        managerPassword: "password",
    )
  }
}
