/*
 * Copyright 2020 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.echo.util

import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.security.AuthenticatedRequest
import com.netflix.spinnaker.security.token.AuthorizationProperties
import com.netflix.spinnaker.security.token.SpinnakerTokenClaims
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ManualJudgmentAuthorizationSpec extends Specification {
  static final String TOKEN = 'test-identity-token'

  def tokenVerifier = Mock(SpinnakerTokenVerifier)
  AuthorizationProperties authorizationProperties = new AuthorizationProperties()

  @Subject
  def manualJudgmentAuthorization = new ManualJudgmentAuthorization(
      Optional.of(tokenVerifier),
      Optional.of(authorizationProperties)
  )

  def cleanup() {
    AuthenticatedRequest.clear()
  }

  @Unroll
  void 'should determine authorization based on intersection of userRoles and stageRoles/permissions'() {
    when:
    def result = manualJudgmentAuthorization.isAuthorized(requiredJudgmentRoles, currentUserRoles)

    then:
    result == isAuthorized

    where:
    requiredJudgmentRoles | currentUserRoles || isAuthorized
    ['foo', 'blaz']       | ['foo', 'baz']   || true
    []                    | ['foo', 'baz']   || true
    []                    | []               || true
    ['foo']               | ['foo']          || true
    ['foo']               | []               || false
    ['foo']               | null             || false
    null                  | null             || true
  }

  void 'should deny when strict and no verified token is available'() {
    given:
    authorizationProperties.enabled = true
    authorizationProperties.strict = true
    // No identity token on the request -> no verified claims.

    when:
    def result = manualJudgmentAuthorization.isAuthorized(['foo'], 'someuser@example.com')

    then: 'the token verifier is never consulted since there is no token'
    0 * tokenVerifier.verify(_)
    !result
  }

  void 'should stay permissive when enabled but not strict and no verified token is available'() {
    given:
    authorizationProperties.enabled = true
    authorizationProperties.strict = false

    expect:
    manualJudgmentAuthorization.isAuthorized(['foo'], 'someuser@example.com')
  }

  void 'should evaluate roles normally with a valid token regardless of strict'() {
    given:
    authorizationProperties.enabled = true
    authorizationProperties.strict = true
    AuthenticatedRequest.set(Header.IDENTITY_TOKEN, TOKEN)

    when:
    def result = manualJudgmentAuthorization.isAuthorized(requiredJudgmentRoles, 'someuser@example.com')

    then:
    1 * tokenVerifier.verify(TOKEN) >> SpinnakerTokenClaims.builder('someuser@example.com').roles(userRoles).build()
    result == expected

    where:
    requiredJudgmentRoles | userRoles      || expected
    ['foo']               | ['foo', 'bar'] || true
    ['foo']               | ['bar']        || false
  }
}
