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
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResizeAsgDescription
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import spock.lang.Shared
import spock.lang.Specification

class ResizeAsgDescriptionValidatorSpec extends Specification {

  @Shared
  DescriptionValidator validator = new ResizeAsgDescriptionValidator()

  void "invalid capacity fails validation"() {
    setup:
    def description = new ResizeAsgDescription(
      asgs: [new ResizeAsgDescription.AsgTargetDescription(
        serverGroupName: "foo",
        region: "us-west-1",
        capacity: new ServerGroup.Capacity(min: 5, max: 3)
      )],
      credentials: Stub(NetflixAmazonCredentials)
    )
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', _, ['5', '3'], _)

    when:
    description.asgs[0].capacity = new ServerGroup.Capacity(min: 3, max: 5, desired: 7)
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', _, ['3', '5', '7'], _)
  }

  void "empty description fails validation"() {
    setup:
    def description = new ResizeAsgDescription()
    description.credentials = TestCredential.named('test')
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("asgs", _)
  }

  void "region is validated against configuration"() {
    setup:
    def description = new ResizeAsgDescription()
    description.credentials = TestCredential.named('test')
    description.asgs = [new ResizeAsgDescription.AsgTargetDescription(
      region: "us-east-5"
    )]
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("regions", _)

    when:
    description.asgs = description.credentials.regions.collect {new ResizeAsgDescription.AsgTargetDescription(
      region: it.name
    )}
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue("regions", _)
  }
}
