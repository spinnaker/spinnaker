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

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AsgDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.SuspendAsgProcessesDescription
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import spock.lang.Specification

class SuspendAsgProcessesDescriptionValidatorSpec extends Specification {

  SuspendAsgProcessesDescriptionValidator validator = new SuspendAsgProcessesDescriptionValidator()

  void "pass validation with proper description inputs"() {
    def description = new SuspendAsgProcessesDescription(
      asgs: [
        new AsgDescription(
          serverGroupName: "asg1",
          region: "us-west-1"
        ),
        new AsgDescription(
          serverGroupName: "asg1",
          region: "us-east-1"
        )
      ],
      processes: ["Launch", "Terminate"]
    )
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }

  void "invalid process names fails validation"() {
    def description = new SuspendAsgProcessesDescription(
      asgs: [
        new AsgDescription(
          serverGroupName: "asg1",
          region: "us-west-1"
        ),
        new AsgDescription(
          serverGroupName: "asg1",
          region: "us-east-1"
        )
      ],
      processes: ["Laugh", "Terminate"]
    )
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("processes", "suspendAsgProcessesDescription.processes.not.valid")
  }

}
