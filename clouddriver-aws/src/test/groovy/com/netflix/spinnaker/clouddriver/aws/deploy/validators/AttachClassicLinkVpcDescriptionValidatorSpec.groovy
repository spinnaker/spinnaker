/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AttachClassicLinkVpcDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class AttachClassicLinkVpcDescriptionValidatorSpec extends Specification {

  @Shared
  AttachClassicLinkVpcDescriptionValidator validator

  void setupSpec() {
    validator = new AttachClassicLinkVpcDescriptionValidator()
  }

  void "invalid instanceId fails validation"() {
    setup:
    def description = new AttachClassicLinkVpcDescription(vpcId: "vpc-123")
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("instanceId", "AttachClassicLinkVpcDescription.instanceId.invalid")
  }

  void "invalid vpcId fails validation"() {
    setup:
    def description = new AttachClassicLinkVpcDescription(instanceId: "i-123")
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("vpcId", "AttachClassicLinkVpcDescription.vpcId.invalid")
  }

  void "unconfigured region fails validation"() {
    setup:
    def description = new AttachClassicLinkVpcDescription(credentials: TestCredential.named('test'))
    description.region = "us-west-5"
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("region", "AttachClassicLinkVpcDescription.region.not.configured")

    when:
    description.region = 'us-east-1'
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue("region", "AttachClassicLinkVpcDescription.region.not.configured")
  }
}
