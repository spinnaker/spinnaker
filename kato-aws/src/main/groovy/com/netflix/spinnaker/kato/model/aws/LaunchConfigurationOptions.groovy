/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kato.model.aws

import com.amazonaws.services.autoscaling.model.*
import com.google.common.collect.Sets
import groovy.transform.Canonical

/**
 * Attributes specified when manipulating launch configurations.
 */
@Canonical class LaunchConfigurationOptions {

  /** @see LaunchConfiguration#launchConfigurationName */
  String launchConfigurationName

  /** @see LaunchConfiguration#imageId */
  String imageId

  /** @see LaunchConfiguration#keyName */
  String keyName

  /** @see LaunchConfiguration#securityGroups */
  Set<String> securityGroups

  /** @see LaunchConfiguration#userData */
  String userData

  /** @see LaunchConfiguration#instanceType */
  String instanceType

  /** @see LaunchConfiguration#kernelId */
  String kernelId

  /** @see LaunchConfiguration#ramdiskId */
  String ramdiskId

  /** @see LaunchConfiguration#blockDeviceMappings */
  Set<BlockDeviceMapping> blockDeviceMappings

  /** @see LaunchConfiguration#instanceMonitoring */
  Boolean instanceMonitoringIsEnabled

  /** Instance price type indicates how instances are priced. */
  InstancePriceType instancePriceType

  /** @see LaunchConfiguration#iamInstanceProfile */
  String iamInstanceProfile

  /** @see LaunchConfiguration#ebsOptimized */
  Boolean ebsOptimized

  /** @see LaunchConfiguration#associatePublicIpAddress */
  Boolean associatePublicIpAddress

  void setSecurityGroups(Collection<String> securityGroups) {
    this.securityGroups = securityGroups == null ? null : Sets.newHashSet(securityGroups)
  }

  void setBlockDeviceMappings(Collection<BlockDeviceMapping> blockDeviceMappings) {
    this.blockDeviceMappings = copyBlockDeviceMappings(blockDeviceMappings)
  }

  @SuppressWarnings('ReturnsNullInsteadOfEmptyCollection')
  private static Set<BlockDeviceMapping> copyBlockDeviceMappings(Collection<BlockDeviceMapping> source) {
    if (source == null) { return null }
    source.collect {
      new BlockDeviceMapping(virtualName: it.virtualName, deviceName: it.deviceName,
        ebs: source.ebs ? new Ebs(snapshotId: it.ebs?.snapshotId, volumeSize: it.ebs?.volumeSize) : null)
    } as Set
  }

  /**
   * Clone options.
   *
   * @param source state
   * @return a deep copy of the source state
   */
  static LaunchConfigurationOptions from(LaunchConfigurationOptions source) {
    new LaunchConfigurationOptions(
      launchConfigurationName: source.launchConfigurationName,
      imageId: source.imageId,
      keyName: source.keyName,
      securityGroups: source.securityGroups == null ? null : Sets.newHashSet(source.securityGroups),
      userData: source.userData,
      instanceType: source.instanceType,
      kernelId: source.kernelId,
      ramdiskId: source.ramdiskId,
      blockDeviceMappings: copyBlockDeviceMappings(source.blockDeviceMappings),
      instanceMonitoringIsEnabled: source.instanceMonitoringIsEnabled,
      instancePriceType: source.instancePriceType,
      iamInstanceProfile: source.iamInstanceProfile,
      ebsOptimized: source.ebsOptimized,
      associatePublicIpAddress: source.associatePublicIpAddress
    )
  }

  /**
   * Copy options from an LaunchConfiguration.
   *
   * @param launchConfiguration state to copy
   * @return a deep copy of the launch configuration
   */
  static LaunchConfigurationOptions from(LaunchConfiguration launchConfiguration) {
    launchConfiguration.with {
      new LaunchConfigurationOptions(
        launchConfigurationName: launchConfigurationName,
        imageId: imageId,
        keyName: keyName,
        securityGroups: securityGroups == null ? null : Sets.newHashSet(securityGroups),
        userData: userData,
        instanceType: instanceType,
        kernelId: kernelId,
        ramdiskId: ramdiskId,
        blockDeviceMappings: copyBlockDeviceMappings(blockDeviceMappings),
        instanceMonitoringIsEnabled: instanceMonitoring?.enabled,
        instancePriceType: spotPrice ? InstancePriceType.SPOT : InstancePriceType.ON_DEMAND,
        iamInstanceProfile: iamInstanceProfile,
        ebsOptimized: ebsOptimized,
        associatePublicIpAddress: associatePublicIpAddress
      )
    }
  }

  /**
   * Construct CreateLaunchConfigurationRequest.
   *
   * @param userContext who made the call, why, and in what region
   * @param spotInstanceRequestService used to recommend spot price
   * @return a CreateLaunchConfigurationRequest based on these options
   */
  CreateLaunchConfigurationRequest getCreateLaunchConfigurationRequest() {
    String spotPrice = null
    new CreateLaunchConfigurationRequest(
      launchConfigurationName: launchConfigurationName,
      imageId: imageId,
      keyName: keyName,
      securityGroups: securityGroups == null ? null : Sets.newHashSet(securityGroups),
      userData: userData,
      instanceType: instanceType,
      kernelId: kernelId ?: null, // Be careful not to set empties here. Null is okay.
      ramdiskId: ramdiskId ?: null, // Be careful not to set empties here. Null is okay.
      blockDeviceMappings: copyBlockDeviceMappings(blockDeviceMappings),
      instanceMonitoring: new InstanceMonitoring().withEnabled(instanceMonitoringIsEnabled ?: false),
      spotPrice: spotPrice,
      iamInstanceProfile: iamInstanceProfile,
      ebsOptimized: ebsOptimized,
      associatePublicIpAddress: associatePublicIpAddress
    )
  }

}
