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

import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AsgDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractConfiguredRegionsValidatorSpec extends Specification {

  @Shared
  DescriptionValidator validator = getDescriptionValidator()

  abstract DescriptionValidator getDescriptionValidator()

  abstract AbstractAmazonCredentialsDescription  getDescription()

  void "empty description fails validation"() {
    setup:
    def description = getDescription()
    description.credentials = TestCredential.named('test')
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("asgs", _)
  }

  void "region is validated against configuration"() {
    setup:
    def description = getDescription()
    description.credentials = TestCredential.named('test')
    description.asgs = [new AsgDescription(
      region: "us-east-5"
    )]
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("regions", _)

    when:
    description.asgs = description.credentials.regions.collect {new AsgDescription(
      region: it.name
    )}
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue("regions", _)
  }
}
