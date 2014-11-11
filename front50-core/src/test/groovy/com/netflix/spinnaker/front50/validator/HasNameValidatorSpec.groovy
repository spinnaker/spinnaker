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

class HasNameValidatorSpec extends Specification {
  @Subject
  HasNameValidator validator = new HasNameValidator()

  def "requires a non-null name"() {
    setup:
    def application = new Application()
    def errors = new ApplicationValidationErrors(application)

    when:
    validator.validate(application, errors)

    then:
    errors.getAllErrors().size() == 1

    when:
    application.name = " "
    errors = new ApplicationValidationErrors(application)
    validator.validate(application, errors)

    then:
    errors.getAllErrors().size() == 1

    when:
    application.name = "application"
    errors = new ApplicationValidationErrors(application)
    validator.validate(application, errors)

    then:
    !errors.hasErrors()
  }
}