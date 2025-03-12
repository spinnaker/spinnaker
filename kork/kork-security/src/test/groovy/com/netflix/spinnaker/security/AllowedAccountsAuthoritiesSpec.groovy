/*
 * Copyright 2020 Netflix, Inc.
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

import org.hamcrest.Matchers
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Specification

import static com.netflix.spinnaker.security.AllowedAccountsAuthorities.PREFIX

class AllowedAccountsAuthoritiesSpec extends Specification {
  def "extracts allowed accounts from granted authorities"() {
    expect:
    AllowedAccountsAuthorities.getAllowedAccounts(userDetails) == expected

    where:
    userDetails               || expected
    null                      || []
    u("foo")                  || []
    u("foo", ["a", "c", "b"]) || ["a", "b", "c"]
  }

  def "builds normalized granted authorities"() {
    expect:
    Matchers.containsInAnyOrder(expected.toArray()).matches(AllowedAccountsAuthorities.buildAllowedAccounts(accounts))

    where:
    accounts                       || expected
    ["A", null, "", "b", "b", "C"] || [a(PREFIX + "a"), a(PREFIX + "b"), a(PREFIX + "c")]
  }

  private static GrantedAuthority a(String name) {
    return new SimpleGrantedAuthority(name)
  }

  private static org.springframework.security.core.userdetails.User u(String name, Collection<String> accounts = []) {
    new org.springframework.security.core.userdetails.User(name, "", accounts.collect { a(PREFIX + it) })
  }
}
