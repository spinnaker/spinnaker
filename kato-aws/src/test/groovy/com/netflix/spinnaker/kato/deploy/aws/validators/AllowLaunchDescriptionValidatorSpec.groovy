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

package com.netflix.spinnaker.kato.deploy.aws.validators

import com.netflix.spinnaker.kato.deploy.aws.description.AllowLaunchDescription
import com.netflix.spinnaker.kato.security.NamedAccountCredentialsHolder
import org.springframework.validation.Errors
import spock.lang.Specification

class AllowLaunchDescriptionValidatorSpec extends Specification {

  void "empty description fails validation"() {
    setup:
    AllowLaunchDescriptionValidator validator = new AllowLaunchDescriptionValidator()
    def description = new AllowLaunchDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("amiName", _)
    1 * errors.rejectValue("region", _)
    1 * errors.rejectValue("account", _)
  }

  void "unconfigured account is rejected"() {
    setup:
    AllowLaunchDescriptionValidator validator = new AllowLaunchDescriptionValidator()
    def credentialsHolder = Mock(NamedAccountCredentialsHolder)
    validator.namedAccountCredentialsHolder = credentialsHolder
    def description = new AllowLaunchDescription(account: "foo")
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * credentialsHolder.getAccountNames() >> { ["prod"] }
    1 * errors.rejectValue("account", _)
  }
}
