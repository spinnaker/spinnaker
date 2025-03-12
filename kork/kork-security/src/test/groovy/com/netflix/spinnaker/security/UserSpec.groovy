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

package com.netflix.spinnaker.security

import spock.lang.Specification

class UserSpec extends Specification {

  def "should not reflect changes to the mutable user in the immutable user"() {
    setup:
      def accounts = ["abc"]
      def mutableUser = new User(email: "email", allowedAccounts: accounts)
      def immutableUser = mutableUser.asImmutable()

    expect:
      mutableUser.email == "email"
      mutableUser.allowedAccounts == ["abc"]
      immutableUser.email == "email"
      immutableUser.username == "email"
      immutableUser.allowedAccounts == ["abc"]

    when:
      mutableUser.email = "batman"
      accounts.add("def")

    then:
      mutableUser.email == "batman"
      mutableUser.allowedAccounts == ["abc", "def"]
      immutableUser.email == "email"
      immutableUser.allowedAccounts == ["abc"]

    when:
      mutableUser.allowedAccounts = ["xyz"]

    then:
      mutableUser.allowedAccounts == ["xyz"]
      immutableUser.allowedAccounts == ["abc"]
  }

  def "should fallback to email if no username is set"() {
    setup:
      def user = new User(email: "email")

    expect:
      user.email == "email"
      user.username == "email"

    when:
      user.username = "username"

    then:
      user.username == "username"
  }

  def "should filter out empty roles"() {
    expect:
      new User(roles: [""]).getAuthorities().isEmpty()
      new User(roles: ["", "bar"]).getAuthorities()*.getAuthority() == ["bar"]
      new User(roles: ["foo", "", "bar"]).getAuthorities()*.getAuthority() == ["foo", "bar"]
  }
}
