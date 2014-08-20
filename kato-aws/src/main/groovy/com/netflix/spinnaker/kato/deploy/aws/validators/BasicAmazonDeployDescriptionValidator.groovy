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


package com.netflix.spinnaker.kato.deploy.aws.validators

import com.netflix.spinnaker.kato.config.AmazonBlockDevice
import com.netflix.spinnaker.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.kato.security.NamedAccountCredentialsHolder
import com.netflix.spinnaker.kato.security.aws.AmazonRoleAccountCredentials
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("basicAmazonDeployDescriptionValidator")
class BasicAmazonDeployDescriptionValidator extends AmazonDescriptionValidationSupport<BasicAmazonDeployDescription> {
  @Autowired
  NamedAccountCredentialsHolder namedAccountCredentialsHolder

  @Override
  void validate(List priorDescriptions, BasicAmazonDeployDescription description, Errors errors) {
    def namedAccountCredentials = null
    def roleBasedCredentials = false

    if (!description.credentials) {
      errors.rejectValue "credentials", "basicAmazonDeployDescription.credentials.empty"
    } else {
      namedAccountCredentials = namedAccountCredentialsHolder.getCredentials(description?.credentials?.environment)
      roleBasedCredentials = namedAccountCredentials instanceof AmazonRoleAccountCredentials
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
    for (String region : description.availabilityZones.keySet()) {
      if (!awsConfigurationProperties.regions?.contains(region) || (roleBasedCredentials && !((AmazonRoleAccountCredentials) namedAccountCredentials).regions*.name?.contains(region))) {
        errors.rejectValue "availabilityZones", "basicAmazonDeployDescription.region.not.configured", [region] as String[], "Region $region not configured"
      }
    }
    for (AmazonBlockDevice device : description.blockDevices) {
      BlockDeviceRules.validate device, errors
    }
    validateCapacity description, errors
  }

  enum BlockDeviceRules {
    deviceNameNotNull({ AmazonBlockDevice device, Errors errors ->
      if (!device.deviceName) {
        errors.rejectValue "blockDevices", "basicAmazonDeployDescription.block.device.not.named", [] as String[], "Device name is required for block device"
      }
    }),

    ephemeralConfigWrong({ AmazonBlockDevice device, Errors errors ->
      if (device.virtualName && (device.deleteOnTermination != null || device.iops || device.size || device.snapshotId || device.volumeType)) {
        errors.rejectValue "blockDevices", "basicAmazonDeployDescription.block.device.ephemeral.config", [device.virtualName] as String[], "Ephemeral block device $device.deviceName with EBS configuration parameters"
      }
    }),

    ebsConfigWrong({ AmazonBlockDevice device, Errors errors ->
      if (!device.virtualName && !device.size) {
        errors.rejectValue "blockDevices", "basicAmazonDeployDescription.block.device.ebs.config", [device.deviceName] as String[], "EBS device $device.deviceName missing required value size"
      }
    })

    private final Closure<Void> validationRule

    BlockDeviceRules(
        @ClosureParams(value = SimpleType, options = [
            'com.netflix.spinnaker.kato.config.AmazonBlockDevice',
            'org.springframework.validation.Errors']) Closure<Void> validationRule) {
      this.validationRule = validationRule
    }

    void validateDevice(AmazonBlockDevice device, Errors errors) {
      validationRule(device, errors)
    }

    static void validate(AmazonBlockDevice device, Errors errors) {
      for (rule in values()) {
        rule.validateDevice device, errors
      }
    }
  }
}
