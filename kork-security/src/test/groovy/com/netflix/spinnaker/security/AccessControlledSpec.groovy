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
import spock.lang.Specification

import static groovy.lang.Tuple.tuple

class AccessControlledSpec extends Specification {
  static class TestCredentials implements AuthorizationMapControlled {
    Map<Authorization, Set<String>> permissions
  }

  static class TestPermissionEvaluator extends AbstractPermissionEvaluator {
    Map<Tuple2<Serializable, String>, Map<String, Collection<Object>>> targetIdTypesToUsernamePermissions = [:]

    @Override
    boolean hasPermission(String username, Serializable targetId, String targetType, Object permission) {
      def targetKey = tuple(targetId, targetType)
      def usernamePermissions = targetIdTypesToUsernamePermissions[targetKey]
      def permissions = usernamePermissions?[username]
      permissions != null && permission in permissions
    }
  }

  static class TestUser implements AuthenticatedPrincipal {
    String name
  }

  void "check hasPermission for expected permissions"() {
    when:
    def username = 'alpha'
    def user = new TestUser(name: username)
    def role = 'dev'
    def authentication = new TestingAuthenticationToken(user, null, [SpinnakerAuthorities.forRoleName(role)])
    def credentials = new TestCredentials(permissions: Map.of(Authorization.READ, Set.of(role), Authorization.WRITE, Set.of('ops')))
    def permissionEvaluator = new TestPermissionEvaluator()
    then:
    permissionEvaluator.hasPermission(authentication, credentials, Authorization.READ)
    !permissionEvaluator.hasPermission(authentication, credentials, Authorization.WRITE)
    !permissionEvaluator.hasPermission(null, credentials, Authorization.READ)
    !permissionEvaluator.hasPermission(authentication, null, Authorization.READ)
    when:
    def targetId = 14
    def targetType = 'entry'
    permissionEvaluator.targetIdTypesToUsernamePermissions[tuple(targetId, targetType)] = Map.of(username, Set.of(Authorization.READ))
    then:
    permissionEvaluator.hasPermission(authentication, targetId, targetType, Authorization.READ)
    permissionEvaluator.hasPermission(username, targetId, targetType, Authorization.READ)
  }
}
