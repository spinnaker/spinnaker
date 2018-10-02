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
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.security.ldap.SpringSecurityLdapTemplate
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class LdapUserRolesProviderTest extends Specification {

  @Shared
  @Subject
  def provider = new LdapUserRolesProvider()

  @Unroll
  void "loadRoles should return no roles for serviceAccouts when userSearchFilter present"() {
    given:
    def user = new ExternalUser(id: 'foo', externalRoles: [new Role(name: 'bar')])

    def configProps = baseConfigProps()
    configProps.groupSearchBase = groupSearchBase
    configProps.userSearchFilter = "notEmpty"

    provider.configProps = configProps
    provider.ldapTemplate = Mock(SpringSecurityLdapTemplate) {
      _ * searchForSingleEntry(*_) >> { throw new IncorrectResultSizeDataAccessException(1) }
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

  void "loadRoles should return no roles for serviceAccouts when userSearchFilter absent"() {
    given:
    def user = new ExternalUser(id: 'foo', externalRoles: [new Role(name: 'bar')])

    def configProps = baseConfigProps()
    configProps.groupSearchBase = groupSearchBase

    provider.configProps = configProps
    provider.ldapTemplate = Mock(SpringSecurityLdapTemplate) {
      (0..1) * searchForSingleEntry(*_) >> { throw new IncorrectResultSizeDataAccessException(1) }
      (0..1) * searchForSingleAttributeValues(*_) >> new HashSet<>()
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

  def baseConfigProps() {
    return new LdapConfig.ConfigProps(
        url: "ldap://monkeymachine:11389/dc=springframework,dc=org",
        managerDn: "manager",
        managerPassword: "password",
    )
  }
}
