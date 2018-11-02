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

package com.netflix.spinnaker.clouddriver.deploy

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.security.resources.AccountNameable
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable
import com.netflix.spinnaker.clouddriver.security.resources.ResourcesNameable
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification
import spock.lang.Subject

class DescriptionAuthorizerSpec extends Specification {
  def registry = new NoopRegistry();
  def evaluator = Mock(FiatPermissionEvaluator)

  @Subject
  DescriptionAuthorizer authorizer = new DescriptionAuthorizer(registry, new ObjectMapper(), Optional.of(evaluator))

  def "should authorize passed description"() {
    given:
    def auth = new TestingAuthenticationToken(null, null)

    def ctx = SecurityContextHolder.createEmptyContext()
    ctx.setAuthentication(auth)
    SecurityContextHolder.setContext(ctx)

    def description = new TestDescription(
      "testAccount", ["testApplication", null], ["testResource1", "testResource2", null]
    )

    def errors = new DescriptionValidationErrors(description)

    when:
    authorizer.authorize(description, errors)

    then:
    4 * evaluator.hasPermission(*_) >> false
    1 * evaluator.storeWholePermission()
    errors.allErrors.size() == 4
  }

  class TestDescription implements AccountNameable, ApplicationNameable, ResourcesNameable {
    String account
    Collection<String> applications
    List<String> names

    TestDescription(String account, Collection<String> applications, List<String> names) {
      this.account = account
      this.applications = applications
      this.names = names
    }
  }
}
