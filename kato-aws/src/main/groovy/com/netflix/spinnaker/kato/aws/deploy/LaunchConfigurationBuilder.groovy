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

package com.netflix.spinnaker.kato.aws.deploy

import com.netflix.spinnaker.kato.aws.model.AmazonBlockDevice
import groovy.transform.Immutable

interface LaunchConfigurationBuilder {

  @Immutable(copyWith = true, knownImmutableClasses = [AmazonBlockDevice])
  static class LaunchConfigurationSettings {
    String account
    String region
    String baseName
    String suffix

    String ami
    String iamRole
    String instanceType
    String keyPair
    Boolean associatePublicIpAddress
    String kernelId
    String ramdiskId
    boolean ebsOptimized
    String spotPrice
    boolean instanceMonitoring
    List<AmazonBlockDevice> blockDevices
    List<String> securityGroups
  }

  /**
   * Extracts the LaunchConfigurationSettings from an existing LaunchConfiguration.
   * @param account the account in which to find the launch configuration
   * @param region the region in which to find the launch configuration
   * @param launchConfigurationName the name of the launch configuration
   * @return LaunchConfigurationSettings for the launch configuration
   */
  LaunchConfigurationSettings buildSettingsFromLaunchConfiguration(String account, String region, String launchConfigurationName)

  /**
   * Constructs an LaunchConfiguration with the provided settings
   * @param application the name of the application - used to construct a default security group if none are present
   * @param subnetType the subnet type for security groups in the launch configuration
   * @param settings the settings for the launch configuration
   * @return the name of the new launch configuration
   */
  String buildLaunchConfiguration(String application, String subnetType, LaunchConfigurationSettings settings)
}
