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
import com.netflix.spinnaker.clouddriver.aws.deploy.InstanceTypeUtils;
import com.netflix.spinnaker.clouddriver.aws.deploy.ModifyServerGroupUtils;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@AmazonOperation(AtomicOperations.UPDATE_LAUNCH_TEMPLATE)
@Component("modifyServerGroupLaunchTemplateDescriptionValidator")
public class ModifyServerGroupLaunchTemplateValidator
    extends AmazonDescriptionValidationSupport<ModifyServerGroupLaunchTemplateDescription> {
  private CredentialsRepository<NetflixAmazonCredentials> credentialsRepository;

  @Autowired
  public ModifyServerGroupLaunchTemplateValidator(
      CredentialsRepository<NetflixAmazonCredentials> credentialsRepository) {
    this.credentialsRepository = credentialsRepository;
  }

  @Override
  public void validate(
      List priorDescriptions,
      ModifyServerGroupLaunchTemplateDescription description,
      ValidationErrors errors) {

    // if only metadata fields are set, fail fast and alert the user as there is nothing to modify
    // in the server group launch template or related config
    if (!areNonMetadataFieldsSet(description)) {
      errors.rejectValue(
          "multiple fields",
          "modifyservergrouplaunchtemplatedescription.launchTemplateAndServerGroupFields.empty",
          "No changes requested to launch template or related server group fields for modifyServerGroupLaunchTemplate operation.");
    }

    String key = ModifyServerGroupLaunchTemplateDescription.class.getSimpleName();
    validateRegion(description, description.getRegion(), key, errors);

    if (description.getCredentials() == null) {
      errors.rejectValue(
          "credentials", "modifyservergrouplaunchtemplatedescription.credentials.empty");
    } else {
      AccountCredentials credentials =
          credentialsRepository.getOne(description.getCredentials().getName());
      if (credentials == null) {
        errors.rejectValue(
            "credentials", "modifyservergrouplaunchtemplatedescription.credentials.invalid");
      }
    }

    if (description.getRegion() == null) {
      errors.rejectValue("region", "modifyservergrouplaunchtemplatedescription.region.empty");
    }

    if (StringUtils.isBlank(description.getAsgName())) {
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

    // unlimitedCpuCredits (set to true / false) is valid only with supported instance types
    if (description.getUnlimitedCpuCredits() != null
        && !InstanceTypeUtils.isBurstingSupportedByAllTypes(description.getAllInstanceTypes())) {
      errors.rejectValue(
          "unlimitedCpuCredits",
          "modifyservergrouplaunchtemplatedescription.bursting.not.supported.by.instanceType");
    }

    // spotInstancePools is applicable only for 'lowest-price' spotAllocationStrategy
    if (description.getSpotInstancePools() != null
        && description.getSpotInstancePools() > 0
        && !description.getSpotAllocationStrategy().equals("lowest-price")) {
      errors.rejectValue(
          "spotInstancePools",
          "modifyservergrouplaunchtemplatedescription.spotInstancePools.not.supported.for.spotAllocationStrategy");
    }
  }

  /**
   * Method that returns a boolean indicating if the description in request has at least 1
   * non-metadata field set.
   *
   * @param descToValidate description to validate
   * @return a boolean, true if at least 1 non-metadata field is set, false otherwise.
   */
  private boolean areNonMetadataFieldsSet(
      final ModifyServerGroupLaunchTemplateDescription descToValidate) {
    return !ModifyServerGroupUtils.getNonMetadataFieldsSetInReq(descToValidate).isEmpty()
        ? true
        : false;
  }
}
