/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.validators;

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@AmazonOperation(AtomicOperations.UPDATE_LAUNCH_TEMPLATE)
@Component("modifyServerGroupLaunchTemplateDescriptionValidator")
public class ModifyServerGroupLaunchTemplateValidator
    extends AmazonDescriptionValidationSupport<ModifyServerGroupLaunchTemplateDescription> {
  private final AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  public ModifyServerGroupLaunchTemplateValidator(
      AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  @Override
  public void validate(
      List priorDescriptions,
      ModifyServerGroupLaunchTemplateDescription description,
      ValidationErrors errors) {
    String key = ModifyServerGroupLaunchTemplateDescription.class.getSimpleName();
    validateRegion(description, description.getRegion(), key, errors);

    if (description.getCredentials() == null) {
      errors.rejectValue(
          "credentials", "modifyservergrouplaunchtemplatedescription.credentials.empty");
    } else {
      AccountCredentials credentials =
          accountCredentialsProvider.getCredentials(description.getCredentials().getName());
      if (!(credentials instanceof AmazonCredentials)) {
        errors.rejectValue(
            "credentials", "modifyservergrouplaunchtemplatedescription.credentials.invalid");
      }
    }

    if (description.getRegion() == null) {
      errors.rejectValue("region", "modifyservergrouplaunchtemplatedescription.region.empty");
    }

    if (description.getAsgName() == null) {
      errors.rejectValue("asgName", "modifyservergrouplaunchtemplatedescription.asgName.empty");
    }

    if (description.getAssociatePublicIpAddress() != null
        && description.getAssociatePublicIpAddress() != null
        && description.getAssociatePublicIpAddress()
        && description.getSubnetType() == null) {
      errors.rejectValue(
          "associatePublicIpAddress",
          "modifyservergrouplaunchtemplatedescription.associatePublicIpAddress.subnetType.not.supplied");
    }

    if (description.getBlockDevices() != null) {
      for (AmazonBlockDevice device : description.getBlockDevices()) {
        BasicAmazonDeployDescriptionValidator.BlockDeviceRules.validate(device, errors);
      }
    }
  }
}
