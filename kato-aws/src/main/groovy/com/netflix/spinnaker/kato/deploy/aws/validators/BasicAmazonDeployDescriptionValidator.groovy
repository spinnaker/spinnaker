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

import com.netflix.bluespar.kato.deploy.aws.description.BasicAmazonDeployDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("basicAmazonDeployDescriptionValidator")
class BasicAmazonDeployDescriptionValidator extends AmazonDescriptionValidationSupport<BasicAmazonDeployDescription> {
  @Override
  void validate(List priorDescriptions, BasicAmazonDeployDescription description, Errors errors) {
    if (!description.credentials) {
      errors.rejectValue "credentials", "basicAmazonDeployDescription.credentials.empty"
    }
    if (!description.application) {
      errors.rejectValue "application", "basicAmazonDeployDescription.application.empty"
    }
    if (!description.amiName) {
      errors.rejectValue "amiName", "basicAmazonDeployDescription.amiName.empty"
    }
    if (!description.instanceType) {
      errors.rejectValue "instanceType", "basicAmazonDeployDescription.instanceType.empty"
    }
    if (!description.availabilityZones) {
      errors.rejectValue "availabilityZones", "basicAmazonDeployDescription.availabilityZones.empty"
    }
    for (String region : description.availabilityZones.keySet()) {
      if (!awsConfigurationProperties.regions.contains(region)) {
        errors.rejectValue "availabilityZones", "basicAmazonDeployDescription.region.not.configured", [region] as String[], "Region $region not configured"
      }
    }
    validateCapacity description, errors
  }
}
