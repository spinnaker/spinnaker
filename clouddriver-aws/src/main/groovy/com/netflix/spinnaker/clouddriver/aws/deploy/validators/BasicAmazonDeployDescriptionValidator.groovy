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

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.clouddriver.aws.AmazonOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.deploy.InstanceTypeUtils
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.credentials.CredentialsRepository
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component("basicAmazonDeployDescriptionValidator")
@AmazonOperation(AtomicOperations.CREATE_SERVER_GROUP)
class BasicAmazonDeployDescriptionValidator extends AmazonDescriptionValidationSupport<BasicAmazonDeployDescription> {
  @Autowired
  CredentialsRepository<NetflixAmazonCredentials> credentialsRepository

  @Override
  void validate(List priorDescriptions, BasicAmazonDeployDescription description, ValidationErrors errors) {
    def credentials = null

    if (!description.credentials) {
      errors.rejectValue "credentials", "basicAmazonDeployDescription.credentials.empty"
    } else {
      credentials = credentialsRepository.getOne(description?.credentials?.name)
      if (credentials == null) {
        errors.rejectValue("credentials", "basicAmazonDeployDescription.credentials.invalid")
      }
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
    if (description.associatePublicIpAddress && !description.subnetType) {
      errors.rejectValue "associatePublicIpAddress", "basicAmazonDeployDescription.associatePublicIpAddress.subnetType.not.supplied"
    }
    if (!description.availabilityZones.values()?.flatten() && !description.subnetType) {
      errors.rejectValue "availabilityZones", "basicAmazonDeployDescription.availabilityZones.or.subnetType.not.supplied"
    }
    if (credentials && !((AmazonCredentials) credentials).regions.name.containsAll(description.availabilityZones.keySet())) {
      errors.rejectValue "availabilityZones", "basicAmazonDeployDescription.region.not.configured", description.availabilityZones.keySet() as String[], "Region not configured"
    }
    for (AmazonBlockDevice device : description.blockDevices) {
      BlockDeviceRules.validate device, errors
    }
    if (!description.source?.useSourceCapacity) {
      validateCapacity description, errors
    }

    // unlimitedCpuCredits (set to true / false) is valid only with supported instance types
    if (description.unlimitedCpuCredits != null && !InstanceTypeUtils.isBurstingSupported(description.instanceType)) {
      errors.rejectValue "unlimitedCpuCredits", "basicAmazonDeployDescription.bursting.not.supported.by.instanceType"
    }

    // log warnings
    final String warnings = getWarnings(description)
    log.warn(warnings)
  }

  /**
   * Log warnings to indicate potential user error, invalid configurations that could result in unexpected outcome, etc.
   */
  @VisibleForTesting
  private String getWarnings(BasicAmazonDeployDescription description) {
    List<String> warnings = []

    // certain features work as expected only when AWS EC2 Launch Template feature is enabled and used
    if (!description.setLaunchTemplate) {
      def ltFeaturesEnabled = []
      if (description.requireIMDSv2) {
        ltFeaturesEnabled.add("requireIMDSv2")
      }
      if (description.associateIPv6Address) {
        ltFeaturesEnabled.add("associateIPv6Address")
      }
      if (description.unlimitedCpuCredits != null) {
        ltFeaturesEnabled.add("unlimitedCpuCredits")
      }
      if (description.placement != null) {
        ltFeaturesEnabled.add("placement")
      }
      if (description.licenseSpecifications != null) {
        ltFeaturesEnabled.add("licenseSpecifications")
      }

      if (ltFeaturesEnabled) {
        warnings.add("WARNING: The following fields ${ltFeaturesEnabled} work as expected only with AWS EC2 Launch Template, " +
                "but 'setLaunchTemplate' is set to false in request with account: ${description.account}, " +
                "application: ${description.application}, stack: ${description.stack})")
      }
    }

    return warnings.join("\n")
  }

  enum BlockDeviceRules {
    deviceNameNotNull({ AmazonBlockDevice device, ValidationErrors errors ->
      if (!device.deviceName) {
        errors.rejectValue "blockDevices", "basicAmazonDeployDescription.block.device.not.named", [] as String[], "Device name is required for block device"
      }
    }),

    ephemeralConfigWrong({ AmazonBlockDevice device, ValidationErrors errors ->
      if (device.virtualName && (device.deleteOnTermination != null || device.iops || device.size || device.snapshotId || device.volumeType)) {
        errors.rejectValue "blockDevices", "basicAmazonDeployDescription.block.device.ephemeral.config", [device.virtualName] as String[], "Ephemeral block device $device.deviceName with EBS configuration parameters"
      }
    }),

    ebsConfigWrong({ AmazonBlockDevice device, ValidationErrors errors ->
      if (!device.virtualName && !device.size) {
        errors.rejectValue "blockDevices", "basicAmazonDeployDescription.block.device.ebs.config", [device.deviceName] as String[], "EBS device $device.deviceName missing required value size"
      }
    })

    private final Closure<Void> validationRule

    BlockDeviceRules(
      @ClosureParams(value = SimpleType, options = [
        'AmazonBlockDevice',
        'org.springframework.validation.Errors']) Closure<Void> validationRule) {
      this.validationRule = validationRule
    }

    void validateDevice(AmazonBlockDevice device, ValidationErrors errors) {
      validationRule(device, errors)
    }

    static void validate(AmazonBlockDevice device, ValidationErrors errors) {
      for (rule in values()) {
        rule.validateDevice device, errors
      }
    }
  }
}
