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

package com.netflix.spinnaker.kato.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.kato.aws.deploy.description.ResizeAsgDescription
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import org.springframework.validation.Errors

class ResizeAsgDescriptionValidatorSpec extends AbstractConfiguredRegionsValidatorSpec {

  @Override
  DescriptionValidator getDescriptionValidator() {
    return new ResizeAsgDescriptionValidator()
  }

  @Override
  ResizeAsgDescription getDescription() {
    new ResizeAsgDescription()
  }

  void "invalid capacity fails validation"() {
    setup:
    def description = new ResizeAsgDescription(asgName: "foo", credentials: Stub(NetflixAmazonCredentials))
    description.capacity.min = 5
    description.capacity.max = 3
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', _, ['5', '3'], _)

    when:
    description.capacity.min = 3
    description.capacity.max = 5
    description.capacity.desired = 7
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', _, ['3', '5', '7'], _)
  }
}
