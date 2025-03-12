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
import org.springframework.security.core.AuthenticatedPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import spock.lang.Specification

import java.security.Principal

class SpinnakerUsersSpec extends Specification {
  void "user id of null authentication returns anonymous"() {
    when:
    def authentication = null
    then:
    SpinnakerUsers.getUserId(authentication) == SpinnakerUsers.ANONYMOUS
  }

  void "user id of UserDetails returns username"() {
    when:
    def username = 'alpha'
    def user = User.withUsername(username).password('').authorities('ROLE_USER').build()
    def authentication = new TestingAuthenticationToken(user, null)
    then:
    SpinnakerUsers.getUserId(authentication) == username
  }

  void "user id of AuthenticatedPrincipal returns name"() {
    when:
    def username = 'beta'
    def principal = new TestAuthenticatedPrincipal(name: username)
    def authentication = new TestingAuthenticationToken(principal, null)
    then:
    SpinnakerUsers.getUserId(authentication) == username
  }

  void "user id of Principal returns name"() {
    when:
    def username = 'gamma'
    def principal = new TestPrincipal(name: username)
    def authentication = new TestingAuthenticationToken(principal, null)
    then:
    SpinnakerUsers.getUserId(authentication) == username
  }

  void "current user id is anonymous by default"() {
    when:
    SecurityContextHolder.context.authentication = null
    AuthenticatedRequest.clear()
    then:
    SpinnakerUsers.currentUserId == SpinnakerUsers.ANONYMOUS
  }

  void "current user id uses current security context first"() {
    when:
    def username = 'delta'
    def principal = new TestPrincipal(name: username)
    SecurityContextHolder.context.authentication = new TestingAuthenticationToken(principal, null)
    AuthenticatedRequest.setUser("not $username")
    then:
    SpinnakerUsers.currentUserId == username
  }

  void "current user id uses authenticated request second"() {
    when:
    def username = 'epsilon'
    SecurityContextHolder.clearContext()
    AuthenticatedRequest.setUser(username)
    then:
    SpinnakerUsers.currentUserId == username
  }

  static class TestPrincipal implements Principal {
    String name
  }

  static class TestAuthenticatedPrincipal implements AuthenticatedPrincipal {
    String name
  }
}
