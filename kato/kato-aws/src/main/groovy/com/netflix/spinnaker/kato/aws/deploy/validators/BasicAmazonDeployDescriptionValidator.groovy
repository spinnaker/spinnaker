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

package com.netflix.spinnaker.kato.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.kato.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperations
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("basicAmazonDeployDescriptionValidator")
@AmazonOperation(AtomicOperations.CREATE_SERVER_GROUP)
class BasicAmazonDeployDescriptionValidator extends AmazonDescriptionValidationSupport<BasicAmazonDeployDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, BasicAmazonDeployDescription description, Errors errors) {
    def credentials = null

    if (!description.credentials) {
      errors.rejectValue "credentials", "basicAmazonDeployDescription.credentials.empty"
    } else {
      credentials = accountCredentialsProvider.getCredentials(description?.credentials?.name)
      if (!(credentials instanceof AmazonCredentials)) {
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
        'com.netflix.spinnaker.kato.aws.model.AmazonBlockDevice',
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
