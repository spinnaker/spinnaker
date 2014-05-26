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

package com.netflix.bluespar.kato.deploy.aws.validators

import com.netflix.bluespar.kato.config.KatoAWSConfig.AwsConfigurationProperties
import com.netflix.bluespar.kato.deploy.DescriptionValidator
import com.netflix.bluespar.kato.deploy.aws.description.ResizeAsgDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("resizeAsgDescriptionValidator")
class ResizeAsgDescriptionValidator extends DescriptionValidator<ResizeAsgDescription> {
  @Autowired
  AwsConfigurationProperties awsConfigurationProperties

  @Override
  void validate(List priorDescriptions, ResizeAsgDescription description, Errors errors) {
    if (!description.asgName) {
      errors.rejectValue("asgName", "resizeAsgDescription.asgName.empty")
    }
    if (!description.regions) {
      errors.rejectValue("regions", "resizeAsgDescription.regions.empty")
    } else if (!awsConfigurationProperties.regions.containsAll(description.regions)) {
      errors.rejectValue("regions", "resizeAsgDescription.regions.not.configured")
    }
    if (description.capacity.min > description.capacity.max) {
      errors.rejectValue "capacity", "resizeAsgDescription.capacity.transposed", [description.capacity.min, description.capacity.max] as String[], "Capacity min and max appear transposed"
    }
    if (description.capacity.desired < description.capacity.min || description.capacity.desired > description.capacity.max) {
      errors.rejectValue "capacity", "resizeAsgDescription.desired.capacity.not.in.range", [description.capacity.min, description.capacity.max, description.capacity.desired] as String[], "Desired capacity is not within min/max range"
    }
  }
}
