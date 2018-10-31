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

import com.netflix.spinnaker.clouddriver.security.resources.AccountNameable
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable
import com.netflix.spinnaker.clouddriver.security.resources.ResourcesNameable
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Subject

class DescriptionValidatorSpec extends Specification {

  def "should authorize passed description"() {
    setup:
    FiatPermissionEvaluator evaluator = Mock(FiatPermissionEvaluator)

    Authentication auth = new TestingAuthenticationToken(null, null)
    SecurityContext ctx = SecurityContextHolder.createEmptyContext()
    ctx.setAuthentication(auth)
    SecurityContextHolder.setContext(ctx)

    @Subject
    TestValidator validator = new TestValidator(permissionEvaluator: evaluator)

    TestDescription description = new TestDescription(account: "testAccount",
                                                      applications: ["testApplication"],
                                                      names: ["thing1", "thing2"])
    Errors errors = new DescriptionValidationErrors(description)

    when:
    validator.authorize(description, errors)

    then:
    4 * evaluator.hasPermission(*_) >> false
    1 * evaluator.storeWholePermission()
    errors.allErrors.size() == 4
  }

  class TestValidator extends DescriptionValidator<TestDescription> {

    @Override
    void validate(List priorDescriptions, TestDescription description, Errors errors) {
    }
  }

  class TestDescription implements AccountNameable, ApplicationNameable, ResourcesNameable {
    String account
    Collection<String> applications
    List<String> names
  }
}
