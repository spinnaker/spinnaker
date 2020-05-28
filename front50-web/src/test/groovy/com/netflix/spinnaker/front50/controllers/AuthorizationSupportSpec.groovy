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

package com.netflix.spinnaker.front50.controllers

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class AuthorizationSupportSpec extends Specification {

  FiatPermissionEvaluator evaluator = Mock(FiatPermissionEvaluator)

  @Subject
  AuthorizationSupport authorizationSupport = new AuthorizationSupport(evaluator)

  @Unroll
  def "should validate run as user access"() {
    expect:
    authorizationSupport.hasRunAsUserPermission(new Pipeline()) == true
    authorizationSupport.hasRunAsUserPermission(new Pipeline(triggers: [])) == true

    when:
    Pipeline p = new Pipeline(application: "app",
                     triggers: [["runAsUser": "service-acct"]])
    def result = authorizationSupport.hasRunAsUserPermission(p)

    then:
    evaluator.hasPermission(_, _, 'SERVICE_ACCOUNT', _) >> userAccess
    evaluator.hasPermission(_, _, 'APPLICATION', _) >> serviceAccountAccess
    result == expected

    where:
    userAccess | serviceAccountAccess || expected
    false      | false                || false
    false      | true                 || false
    true       | false                || false
    true       | true                 || true
  }
}
