package com.netflix.spinnaker.gate.config

import com.netflix.spinnaker.gate.security.anonymous.AnonymousSecurityConfig
import com.netflix.spinnaker.gate.security.onelogin.saml.Response
import com.netflix.spinnaker.security.User
import spock.lang.Specification
import spock.lang.Unroll

class OneLoginSecurityConfigSpec extends Specification {
  @Unroll
  void "should parse LDAP CN's into roles"() {
    given:
    def oneLoginSecurityConfig = new OneLoginSecurityConfig.OneLoginSecurityConfigProperties(
      requiredRoleByAccount: ["test": "groupA"]
    )
    def commonNames = "CN=groupA,OU=Groups,DC=netflix,DC=com;CN=groupB,DC=netflix,DC=com;"
    def response = Mock(Response) {
      1 * getAttributes() >> { [memberOf: [commonNames]] }
      1 * getNameId() >> { "test@netflix.com" }
      1 * getAttribute("User.FirstName") >> { "FirstName" }
      1 * getAttribute("User.LastName") >> { "LastName" }
      0 * _
    }

    when:
    def user = OneLoginSecurityConfig.OneLoginSecurityController.buildUser(oneLoginSecurityConfig, response, allowedAnonymousAccounts)

    then:
    user.email == "test@netflix.com"
    user.firstName == "FirstName"
    user.lastName == "LastName"
    user.roles == ["groupa", "groupb"]
    user.allowedAccounts.sort() == expectedAllowedAccounts

    where:
    allowedAnonymousAccounts || expectedAllowedAccounts
    ["anonymous"]            || ["anonymous", "test"]
    []                       || ["test"]
    null                     || ["test"]
  }

  @Unroll
  void "should check for anonymous allowed accounts when determining whether user has required roles"() {
    given:
    def anonymousSecurityConfig = (anonymousAllowedAccounts != null) ? new AnonymousSecurityConfig(allowedAccounts: anonymousAllowedAccounts) : null
    def oneLoginSecurityConfig = new OneLoginSecurityConfig.OneLoginSecurityConfigProperties(
      requiredRoleByAccount: requiredRolesByAccount
    )
    def user = new User(email: email, roles: userRoles)

    expect:
    OneLoginSecurityConfig.OneLoginSecurityController.hasRequiredRole(anonymousSecurityConfig, oneLoginSecurityConfig, user) == hasRequiredRole

    where:
    email        | requiredRolesByAccount | anonymousAllowedAccounts | userRoles  || hasRequiredRole
    "authorized" | ["test": "groupA"]     | ["prod"]                 | []         || true
    "anonymous"  | ["test": "groupA"]     | ["prod"]                 | []         || false
    "authorized" | ["test": "groupA"]     | []                       | []         || false
    "authorized" | ["test": "groupA"]     | null                     | []         || false
    "authorized" | ["test": "groupA"]     | null                     | ["groupA"] || true
    "authorized" | ["test": "groupA"]     | null                     | ["groupA"] || true
  }
}
