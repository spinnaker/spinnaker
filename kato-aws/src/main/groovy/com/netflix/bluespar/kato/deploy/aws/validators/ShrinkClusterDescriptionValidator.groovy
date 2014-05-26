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

import com.netflix.bluespar.kato.config.KatoAWSConfig
import com.netflix.bluespar.kato.deploy.DescriptionValidator
import com.netflix.bluespar.kato.deploy.aws.description.ShrinkClusterDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("shrinkClusterDescriptionValidator")
class ShrinkClusterDescriptionValidator extends DescriptionValidator<ShrinkClusterDescription> {
  @Autowired
  KatoAWSConfig.AwsConfigurationProperties awsConfigurationProperties

  @Override
  void validate(List priorDescriptions, ShrinkClusterDescription description, Errors errors) {
    if (!description.application) {
      errors.rejectValue("application", "shrinkClusterDescription.application.empty")
    }
    if (!description.clusterName) {
      errors.rejectValue("clusterName", "shrinkClusterDescription.clusterName.empty")
    }
    if (!description.regions) {
      errors.rejectValue("regions", "resizeAsgDescription.regions.empty")
    } else if (!awsConfigurationProperties.regions.containsAll(description.regions)) {
      errors.rejectValue("regions", "resizeAsgDescription.regions.not.configured")
    }
  }
}
