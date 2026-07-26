/*
 * Copyright 2026 McIntosh.farm
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

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AsgDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertWarmPoolDescription
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import spock.lang.Specification
import spock.lang.Unroll

class UpsertWarmPoolDescriptionValidatorSpec extends Specification {

  UpsertWarmPoolDescriptionValidator validator = new UpsertWarmPoolDescriptionValidator()

  void "pass validation with proper description inputs"() {
    def description = new UpsertWarmPoolDescription(
      asgs: [
        new AsgDescription(serverGroupName: "asg1", region: "us-west-1")
      ],
      minSize: 1,
      maxGroupPreparedCapacity: 5,
      poolState: "Stopped",
      reuseOnScaleIn: true
    )
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }

  void "missing asgs fails validation"() {
    def description = new UpsertWarmPoolDescription(
      asgs: [],
      minSize: 1,
      maxGroupPreparedCapacity: 5,
      poolState: "Stopped"
    )
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("asgs", "UpsertWarmPoolDescription.empty")
  }

  void "negative minSize fails validation"() {
    def description = new UpsertWarmPoolDescription(
      asgs: [new AsgDescription(serverGroupName: "asg1", region: "us-west-1")],
      minSize: -1,
      poolState: "Stopped"
    )
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("minSize", "upsertWarmPoolDescription.minSize.invalid")
  }

  void "maxGroupPreparedCapacity less than minSize fails validation"() {
    def description = new UpsertWarmPoolDescription(
      asgs: [new AsgDescription(serverGroupName: "asg1", region: "us-west-1")],
      minSize: 5,
      maxGroupPreparedCapacity: 2,
      poolState: "Stopped"
    )
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("maxGroupPreparedCapacity", "upsertWarmPoolDescription.maxGroupPreparedCapacity.transposed")
  }

  @Unroll
  void "invalid poolState '#poolState' fails validation"() {
    def description = new UpsertWarmPoolDescription(
      asgs: [new AsgDescription(serverGroupName: "asg1", region: "us-west-1")],
      poolState: poolState
    )
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("poolState", "upsertWarmPoolDescription.poolState.not.valid")

    where:
    poolState << ["Sleeping", "running"]
  }
}
