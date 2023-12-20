/*
 * Copyright 2023 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.security

import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import spock.lang.Specification

class SpinnakerAuthoritiesSpec extends Specification {
  void "isAdmin returns expected value"() {
    expect:
    SpinnakerAuthorities.isAdmin(authentication) == isAdmin
    where:
    authentication                              || isAdmin
    null                                        || false
    u(SpinnakerAuthorities.ADMIN_AUTHORITY)     || true
    u(SpinnakerAuthorities.ANONYMOUS_AUTHORITY) || false
  }

  void "hasRole returns expected value"() {
    expect:
    SpinnakerAuthorities.hasRole(authentication, 'dev') == hasRole
    where:
    authentication                              || hasRole
    null                                        || false
    u('ROLE_dev')                               || true
    u(SpinnakerAuthorities.forRoleName('dev'))  || true
    u(SpinnakerAuthorities.ANONYMOUS_AUTHORITY) || false
  }

  void "getRoles returns expected value"() {
    expect:
    SpinnakerAuthorities.getRoles(authentication) == expectedRoles
    where:
    authentication                           || expectedRoles
    null                                     || []
    u('ROLE_a', 'ROLE_b')                    || ['a', 'b']
    u(SpinnakerAuthorities.forRoleName('c')) || ['c']
  }

  private static Authentication u(String... authorities) {
    new TestingAuthenticationToken(null, null, authorities)
  }

  private static Authentication u(GrantedAuthority... authorities) {
    new TestingAuthenticationToken(null, null, List.of(authorities))
  }
}
