/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.front50.validator

import com.netflix.spinnaker.front50.model.application.Application
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class HasEmailValidatorSpec extends Specification {
  @Subject
  ApplicationValidator validator = new HasEmailValidator()

  @Unroll
  def "'#email' is #description email"() {
    setup:
    def application = new Application()
    def errors = new ApplicationValidationErrors(application)

    when:
    application.email = email
    validator.validate(application, errors)

    then:
    errors.getAllErrors().size() == numberOfErrors

    where:
    email | numberOfErrors
    null  | 1
    ""    | 1
    " "   | 1
    "email@netflix.com" | 0

    description = (numberOfErrors > 0) ? "an empty" : "a non-empty"
  }
}