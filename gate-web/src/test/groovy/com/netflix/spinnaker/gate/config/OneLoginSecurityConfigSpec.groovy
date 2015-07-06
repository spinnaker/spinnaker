package com.netflix.spinnaker.gate.config

import com.netflix.spinnaker.gate.security.onelogin.saml.Response
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
}
