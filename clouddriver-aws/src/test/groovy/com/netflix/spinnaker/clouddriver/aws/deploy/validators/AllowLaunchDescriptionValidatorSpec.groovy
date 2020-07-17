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

package com.netflix.spinnaker.clouddriver.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AllowLaunchDescription
import spock.lang.Specification

class AllowLaunchDescriptionValidatorSpec extends Specification {

  void "empty description fails validation"() {
    setup:
    AllowLaunchDescriptionValidator validator = new AllowLaunchDescriptionValidator()
    def description = new AllowLaunchDescription()
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("amiName", _)
    1 * errors.rejectValue("region", _)
    1 * errors.rejectValue("targetAccount", _)
  }

  void "unconfigured account is rejected"() {
    setup:
    AllowLaunchDescriptionValidator validator = new AllowLaunchDescriptionValidator()
    def credentialsHolder = Mock(AccountCredentialsProvider)
    validator.accountCredentialsProvider = credentialsHolder
    def description = new AllowLaunchDescription(targetAccount: "foo")
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * credentialsHolder.getAll() >> { [TestCredential.named('prod')] }
    1 * errors.rejectValue("targetAccount", _)
  }
}
