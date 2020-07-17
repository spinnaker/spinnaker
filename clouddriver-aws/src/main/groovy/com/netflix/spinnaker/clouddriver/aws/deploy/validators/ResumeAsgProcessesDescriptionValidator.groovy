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

import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResumeAsgProcessesDescription
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import org.springframework.stereotype.Component

@Component
class ResumeAsgProcessesDescriptionValidator extends AmazonDescriptionValidationSupport<ResumeAsgProcessesDescription> {
  @Override
  void validate(List priorDescriptions, ResumeAsgProcessesDescription description, ValidationErrors errors) {
    validateAsgs description, errors
    def invalidProcessTypes = description.processes.findAll { !AutoScalingProcessType.parse(it) }
    if (invalidProcessTypes) {
      errors.rejectValue "processes", "createNetworkInterfaceDescription.processes.not.valid"
    }
  }
}
