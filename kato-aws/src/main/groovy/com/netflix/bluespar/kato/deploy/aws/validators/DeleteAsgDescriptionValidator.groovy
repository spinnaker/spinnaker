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
import com.netflix.bluespar.kato.deploy.aws.description.DeleteAsgDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("deleteAsgDescriptionValidator")
class DeleteAsgDescriptionValidator extends DescriptionValidator<DeleteAsgDescription> {
  @Autowired
  AwsConfigurationProperties awsConfigurationProperties

  @Override
  void validate(List priorDescriptions, DeleteAsgDescription description, Errors errors) {
    if (!description.asgName) {
      errors.rejectValue("asgName", "deleteAsgDescription.asgName.empty")
    }
    if (!description.regions) {
      errors.rejectValue("regions", "deleteAsgDescription.regions.empty")
    } else if (description.regions && !awsConfigurationProperties.regions.containsAll(description.regions)) {
      errors.rejectValue("regions", "deleteAsgDescription.regions.not.configured")
    }
  }
}
