/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.asg;

import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice;
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import java.util.List;
import lombok.Builder;
import lombok.Value;

public interface LaunchConfigurationBuilder {

  /**
   * Extracts the LaunchConfigurationSettings from an existing LaunchConfiguration.
   *
   * @param credentials the account in which to find the launch configuration
   * @param region the region in which to find the launch configuration
   * @param launchConfigurationName the name of the launch configuration
   * @return LaunchConfigurationSettings for the launch configuration
   */
  LaunchConfigurationSettings buildSettingsFromLaunchConfiguration(
      AccountCredentials<?> credentials, String region, String launchConfigurationName);
  /**
   * Constructs an LaunchConfiguration with the provided settings
   *
   * @param application the name of the application - used to construct a default security group if
   *     none are present
   * @param subnetType the subnet type for security groups in the launch configuration
   * @param settings the settings for the launch configuration
   * @param legacyUdf whether to explicitly use or not use legacyUdf mode - can be null which will
   *     fall through to application default
   * @param userDataOverride - whether to allow the user supplied user data to override any default
   *     user data
   * @return the name of the new launch configuration
   */
  String buildLaunchConfiguration(
      String application,
      String subnetType,
      LaunchConfigurationSettings settings,
      Boolean legacyUdf,
      UserDataOverride userDataOverride);

  @Value
  class LaunchConfigurationSettings {
    String account;
    String environment;
    String accountType;
    String region;
    String baseName;
    String suffix;
    String ami;
    String iamRole;
    String classicLinkVpcId;
    List<String> classicLinkVpcSecurityGroups;
    String instanceType;
    String keyPair;
    String base64UserData;
    Boolean associatePublicIpAddress;
    String kernelId;
    String ramdiskId;
    boolean ebsOptimized;
    String spotPrice;
    boolean instanceMonitoring;
    List<AmazonBlockDevice> blockDevices;
    List<String> securityGroups;

    @Builder(toBuilder = true)
    private LaunchConfigurationSettings(
        String account,
        String environment,
        String accountType,
        String region,
        String baseName,
        String suffix,
        String ami,
        String iamRole,
        String classicLinkVpcId,
        List<String> classicLinkVpcSecurityGroups,
        String instanceType,
        String keyPair,
        String base64UserData,
        Boolean associatePublicIpAddress,
        String kernelId,
        String ramdiskId,
        boolean ebsOptimized,
        String spotPrice,
        boolean instanceMonitoring,
        List<AmazonBlockDevice> blockDevices,
        List<String> securityGroups) {
      this.account = account;
      this.environment = environment;
      this.accountType = accountType;
      this.region = region;
      this.baseName = baseName;
      this.suffix = suffix;
      this.ami = ami;
      this.iamRole = iamRole;
      this.classicLinkVpcId = classicLinkVpcId;
      this.classicLinkVpcSecurityGroups = classicLinkVpcSecurityGroups;
      this.instanceType = instanceType;
      this.keyPair = keyPair;
      this.base64UserData = base64UserData;
      this.associatePublicIpAddress = associatePublicIpAddress;
      this.kernelId = kernelId;
      this.ramdiskId = ramdiskId;
      this.ebsOptimized = ebsOptimized;
      this.spotPrice = spotPrice;
      this.instanceMonitoring = instanceMonitoring;
      this.blockDevices = blockDevices;
      this.securityGroups = securityGroups;
    }
  }
}
