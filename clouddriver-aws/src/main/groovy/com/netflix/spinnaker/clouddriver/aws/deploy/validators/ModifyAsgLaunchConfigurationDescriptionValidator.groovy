/*
 * Copyright 2015 Netflix, Inc.
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

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyAsgLaunchConfigurationDescription
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@AmazonOperation(AtomicOperations.UPDATE_LAUNCH_CONFIG)
@Component("modifyAsgLaunchConfigurationDescriptionValidator")
class ModifyAsgLaunchConfigurationDescriptionValidator extends AmazonDescriptionValidationSupport<ModifyAsgLaunchConfigurationDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, ModifyAsgLaunchConfigurationDescription description, ValidationErrors errors) {
    def key = ModifyAsgLaunchConfigurationDescription.class.simpleName
    validateRegion(description, description.region, key, errors)

    if (!description.credentials) {
      errors.rejectValue "credentials", "modifyAsgLaunchConfigurationDescription.credentials.empty"
    } else {
      def credentials = accountCredentialsProvider.getCredentials(description?.credentials?.name)
      if (!(credentials instanceof AmazonCredentials)) {
        errors.rejectValue("credentials", "modifyAsgLaunchConfigurationDescription.credentials.invalid")
      }
    }
    if (!description.region) {
      errors.rejectValue "region", "modifyAsgLaunchConfigurationDescription.region.empty"
    }
    if (!description.asgName) {
      errors.rejectValue "asgName", "modifyAsgLaunchConfigurationDescription.asgName.empty"
    }
    if (description.associatePublicIpAddress && !description.subnetType) {
      errors.rejectValue "associatePublicIpAddress", "modifyAsgLaunchConfigurationDescription.associatePublicIpAddress.subnetType.not.supplied"
    }
    for (AmazonBlockDevice device : (description.blockDevices ?: [])) {
      BasicAmazonDeployDescriptionValidator.BlockDeviceRules.validate device, errors
    }

  }
}
