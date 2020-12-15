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

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatStatus
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ManualJudgmentAuthorizationSpec extends Specification {
  def fiatPermissionEvaluator = Mock(FiatPermissionEvaluator)
  def fiatStatus = Mock(FiatStatus)

  @Subject
  def manualJudgmentAuthorization = new ManualJudgmentAuthorization(
      Optional.of(fiatPermissionEvaluator),
      fiatStatus
  )

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
}
