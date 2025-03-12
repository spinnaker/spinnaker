/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1.security

import com.netflix.spinnaker.halyard.config.model.v1.security.Authn
import com.netflix.spinnaker.halyard.config.model.v1.security.OAuth2
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder
import com.netflix.spinnaker.halyard.core.problem.v1.Problem
import spock.lang.Specification

class AuthnValidatorSpec extends Specification {

  AuthnValidator validator
  ConfigProblemSetBuilder problemSetBuilder

  def setup() {
    problemSetBuilder = new ConfigProblemSetBuilder()
    validator = new AuthnValidator()
  }

  def "should catch cases when core fields are set"() {
    setup:
    Authn n = new Authn()

    when:
    validator.validate(problemSetBuilder.reset(), n)
    def problems = problemSetBuilder.build().problems

    then:
    problems.empty

    when:
    OAuth2 o = new OAuth2(client: new OAuth2.Client(clientId: "foo"))
    n = new Authn(oauth2: o)
    validator.validate(problemSetBuilder.reset(), n)
    problems = problemSetBuilder.build().problems

    then:
    !problems.empty
    problems.first().severity == Problem.Severity.WARNING

    when:
    o.enabled = true
    validator.validate(problemSetBuilder.reset(), n)
    problems = problemSetBuilder.build().problems

    then:
    problems.empty
  }
}
