/*
 * Copyright 2020 Netflix, Inc.
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


package com.netflix.spinnaker.front50.validator

import com.netflix.spinnaker.front50.model.application.Application
import spock.lang.Specification
import spock.lang.Unroll

class ApplicationNameValidatorSpec extends Specification {

  @Unroll
  def "validates #appName isValid: #isValid"() {
    setup:
    ApplicationNameValidatorConfigurationProperties properties = new ApplicationNameValidatorConfigurationProperties()
    properties.setValidationRegex('^[a-zA-Z0-9.\\-_]*$')
    ApplicationNameValidator validator = new ApplicationNameValidator(properties)
    def application = new Application()
    def errors = new ApplicationValidationErrors(application)

    when:
    application.name = appName
    validator.validate(application, errors)

    then:
    errors.getAllErrors().size() == (isValid ? 0 : 1)

    where:
    appName         || isValid
    "validname"     || true
    "valid1name"    || true
    "valid-name"    || true
    "valid1.name"   || true
    "valid-1_name"  || true
    "invalid!name"  || false
    "invalid name"  || false
    "invalid.имя"   || false
  }

  @Unroll
  def "does not validate when no regex supplied"() {
    setup:
    def application = new Application()
    application.name = 'søme wéird name!'
    def errors = new ApplicationValidationErrors(application)

    when:
    ApplicationNameValidatorConfigurationProperties properties = new ApplicationNameValidatorConfigurationProperties()
    properties.setValidationRegex('')
    ApplicationNameValidator validator = new ApplicationNameValidator(properties)
    validator.validate(application, errors)

    then:
    errors.getAllErrors().size() == 0

    when:
    validator = new ApplicationNameValidator(new ApplicationNameValidatorConfigurationProperties())
    validator.validate(application, errors)

    then:
    errors.getAllErrors().size() == 0
  }

  def "uses optional error message"() {
    setup:
    def application = new Application()
    application.name = 'noncompliantname!'
    def errors = new ApplicationValidationErrors(application)

    when:
    ApplicationNameValidatorConfigurationProperties properties = new ApplicationNameValidatorConfigurationProperties()
    properties.setValidationRegex('strictname')
    ApplicationNameValidator validator = new ApplicationNameValidator(properties)
    validator.validate(application, errors)

    then:
    errors.getAllErrors().size() == 1
    errors.getAllErrors()[0].defaultMessage == "Application name doesn't satisfy the validation regex: " + properties.getValidationRegex()

    when:
    errors = new ApplicationValidationErrors(application)
    properties.setValidationMessage("a validation message")
    validator.validate(application, errors)

    then:
    errors.getAllErrors().size() == 1
    errors.getAllErrors()[0].defaultMessage == properties.getValidationMessage()
  }
}
