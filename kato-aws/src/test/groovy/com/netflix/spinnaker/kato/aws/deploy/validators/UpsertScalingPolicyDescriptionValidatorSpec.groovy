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

import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertScalingPolicyDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class UpsertScalingPolicyDescriptionValidatorSpec extends Specification {

  @Subject validator = new UpsertScalingPolicyDescriptionValidator()

  def description = new UpsertScalingPolicyDescription(
    credentials: TestCredential.named('test'),
    name: "dansScalingPolicy",
    asgName: "kato-main-v000",
    region: "us-west-1",
    metric: new UpsertScalingPolicyDescription.Metric([
      name: "CPUUtilization",
      namespace: "AWS/EC2"
    ]),
    threshold: 81,
    scaleAmount: 1
  )

  @Shared
  Errors errors

  def setup() {
    errors = Mock(Errors)
  }

  void "empty description fails validation"() {
    setup:
    def description = new UpsertScalingPolicyDescription()

    when:
    validator.validate([], description, errors)

    then:
    _ * errors.rejectValue(_, _)
  }

  void "region is validates against configuration"() {
    setup:
    def description = getDescription()
    description.region = "us-east-5"

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("regions", _)

    when:
    description.region = 'us-east-1'
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue("regions", _)
  }
}
