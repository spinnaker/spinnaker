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

package com.netflix.spinnaker.gate.security.oauth2

import spock.lang.Specification
import spock.lang.Subject

class SpinnakerUserInfoTokenServicesSpec extends Specification {

  def "should evaluate userInfoRequirements against authentication details"() {
    setup:
    def userInfoRequirements = new OAuth2SsoConfig.UserInfoRequirements()
    @Subject tokenServices = new SpinnakerUserInfoTokenServices(userInfoRequirements: userInfoRequirements)

    expect: "no domain restriction means everything matches"
    tokenServices.hasAllUserInfoRequirements([:])
    tokenServices.hasAllUserInfoRequirements(["hd": "foo.com"])
    tokenServices.hasAllUserInfoRequirements(["bar": "foo.com"])
    tokenServices.hasAllUserInfoRequirements(["bar": "bar.com"])

    when: "domain restricted but not found on userAuthorizationUri"
    userInfoRequirements.hd = "foo.com"

    then:
    !tokenServices.hasAllUserInfoRequirements([:])
    tokenServices.hasAllUserInfoRequirements(["hd": "foo.com"])
    !tokenServices.hasAllUserInfoRequirements(["bar": "foo.com"])
    !tokenServices.hasAllUserInfoRequirements(["bar": "bar.com"])

    when: 'domain restricted by regex expression'
    userInfoRequirements.hd = "/foo\\.com|bar\\.com/"

    then:
    !tokenServices.hasAllUserInfoRequirements([:])
    tokenServices.hasAllUserInfoRequirements(['hd': 'foo.com'])
    tokenServices.hasAllUserInfoRequirements(['hd': 'bar.com'])
    !tokenServices.hasAllUserInfoRequirements(['hd': 'baz.com'])
    !tokenServices.hasAllUserInfoRequirements(['bar': 'foo.com'])

    when: "multiple restriction values"
    userInfoRequirements.bar = "bar.com"

    then:
    !tokenServices.hasAllUserInfoRequirements(["hd": "foo.com"])
    !tokenServices.hasAllUserInfoRequirements(["bar": "bar.com"])
    tokenServices.hasAllUserInfoRequirements(["hd": "foo.com", "bar": "bar.com"])

    when: "evaluating a list, any match is accepted"
    userInfoRequirements.clear()
    userInfoRequirements.roles = "expected-role"

    then:
    tokenServices.hasAllUserInfoRequirements("roles": "expected-role")
    tokenServices.hasAllUserInfoRequirements("roles": ["expected-role", "unexpected-role"])
    !tokenServices.hasAllUserInfoRequirements([:])
    !tokenServices.hasAllUserInfoRequirements("roles": "unexpected-role")
    !tokenServices.hasAllUserInfoRequirements("roles": ["unexpected-role"])

    when: "evaluating a regex in a list, any match is accepted"
    userInfoRequirements.roles = "/^.+_ADMIN\$/"

    then:
    tokenServices.hasAllUserInfoRequirements(roles: "foo_ADMIN")
    tokenServices.hasAllUserInfoRequirements(roles: ["foo_ADMIN"])
    !tokenServices.hasAllUserInfoRequirements(roles: ["_ADMIN", "foo_USER"])
    !tokenServices.hasAllUserInfoRequirements(roles: ["foo_ADMINISTRATOR", "bar_USER"])
  }

  def "should extract roles from details"() {
    given:
    def tokenServices = new SpinnakerUserInfoTokenServices(userInfoMapping: new OAuth2SsoConfig.UserInfoMapping(roles: 'roles'))
    def details = [
      roles: rolesValue
    ]

    expect:
    tokenServices.getRoles(details) == expectedRoles

    where:
    rolesValue                || expectedRoles
    null                      || []
    ""                        || []
    ["foo", "bar"]            || ["foo", "bar"]
    "foo,bar"                 || ["foo", "bar"]
    "foo bar"                 || ["foo", "bar"]
    "foo"                     || ["foo"]
    "foo   bar"               || ["foo", "bar"]
    "foo,,,bar"               || ["foo", "bar"]
    "foo, bar"                || ["foo", "bar"]
    ["[]"]                    || []
    ["[\"foo\"]"]             || ["foo"]
    ["[\"foo\", \"bar\"]"]    || ["foo", "bar"]
    1                         || []
    [blergh: "blarg"]         || []
  }
}
