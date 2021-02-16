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

package com.netflix.spinnaker.clouddriver.aws.deploy.asg

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AlreadyExistsException
import com.amazonaws.services.autoscaling.model.BlockDeviceMapping
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest
import com.amazonaws.services.autoscaling.model.Ebs
import com.amazonaws.services.autoscaling.model.InstanceMonitoring
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProviderAggregator
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataInput
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.LocalFileUserDataProperties
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller

import groovy.util.logging.Slf4j
import org.joda.time.LocalDateTime

@Slf4j
class DefaultLaunchConfigurationBuilder implements LaunchConfigurationBuilder {

  final AmazonAutoScaling autoScaling
  final AsgService asgService
  final SecurityGroupService securityGroupService
  final UserDataProviderAggregator userDataProviderAggregator
  final LocalFileUserDataProperties localFileUserDataProperties
  final DeployDefaults deployDefaults

  DefaultLaunchConfigurationBuilder(AmazonAutoScaling autoScaling, AsgService asgService,
                                    SecurityGroupService securityGroupService,
                                    UserDataProviderAggregator userDataProviderAggregator,
                                    LocalFileUserDataProperties localFileUserDataProperties,
                                    DeployDefaults deployDefaults) {
    this.autoScaling = autoScaling
    this.asgService = asgService
    this.securityGroupService = securityGroupService
    this.userDataProviderAggregator = userDataProviderAggregator
    this.localFileUserDataProperties = localFileUserDataProperties
    this.deployDefaults = deployDefaults
  }

  /**
   * Extracts the LaunchConfigurationSettings from an existing LaunchConfiguration.
   * @param account the account in which to find the launch configuration
   * @param region the region in which to find the launch configuration
   * @param launchConfigurationName the name of the launch configuration
   * @return LaunchConfigurationSettings for the launch configuration
   */
  @Override
  LaunchConfigurationSettings buildSettingsFromLaunchConfiguration(AccountCredentials<?> account, String region, String launchConfigurationName) {
    LaunchConfiguration lc = asgService.getLaunchConfiguration(launchConfigurationName)

    String baseName = lc.launchConfigurationName
    String suffix = null
    int suffixLoc = lc.launchConfigurationName.lastIndexOf('-')
    if (suffixLoc != -1) {
      baseName = lc.launchConfigurationName.substring(0, suffixLoc)
      suffix = lc.launchConfigurationName.substring(suffixLoc + 1)
    }

    List<AmazonBlockDevice> blockDevices = lc.blockDeviceMappings.collect { BlockDeviceMapping mapping ->
      if (mapping.ebs) {
        new AmazonBlockDevice(deviceName: mapping.deviceName,
          size: mapping.ebs.volumeSize,
          volumeType: mapping.ebs.volumeType,
          deleteOnTermination: mapping.ebs.deleteOnTermination,
          iops: mapping.ebs.iops,
          snapshotId: mapping.ebs.snapshotId,
          encrypted: mapping.ebs.encrypted)
      } else {
        new AmazonBlockDevice(deviceName: mapping.deviceName, virtualName: mapping.virtualName)
      }
    }

    /*
      Copy over the original user data only if the UserDataProviders behavior is disabled.
      This is to avoid having duplicate user data.
     */
    String base64UserData = (localFileUserDataProperties && !localFileUserDataProperties.enabled) ? lc.userData : null

    LaunchConfigurationSettings.builder()
      .account(account.name)
      .environment(account.environment)
      .accountType(account.accountType)
      .region(region)
      .baseName(baseName)
      .suffix(suffix)
      .ami(lc.imageId)
      .iamRole(lc.iamInstanceProfile)
      .classicLinkVpcId(lc.classicLinkVPCId)
      .classicLinkVpcSecurityGroups(lc.classicLinkVPCSecurityGroups)
      .instanceType(lc.instanceType)
      .keyPair(lc.keyName)
      .associatePublicIpAddress(lc.associatePublicIpAddress)
      .kernelId(lc.kernelId ?: null)
      .ramdiskId(lc.ramdiskId ?: null)
      .ebsOptimized(lc.ebsOptimized)
      .spotPrice(lc.spotPrice)
      .instanceMonitoring(lc.instanceMonitoring == null ? false : lc.instanceMonitoring.enabled)
      .blockDevices(blockDevices)
      .securityGroups(lc.securityGroups)
      .base64UserData(base64UserData)
      .build()
  }

  /**
   * Constructs a LaunchConfiguration with the provided settings
   * @param application the name of the application - used to construct a default security group if none are present
   * @param subnetType the subnet type for security groups in the launch configuration
   * @param settings the settings for the launch configuration
   * @param whether to explicitly use or not use legacyUdf mode - can be null which will fall through to application default
   * @return the name of the new launch configuration
   */
  @Override
  String buildLaunchConfiguration(String application, String subnetType, LaunchConfigurationSettings settings, Boolean legacyUdf, UserDataOverride userDataOverride) {
    settings = setAppSecurityGroup(application, subnetType, deployDefaults, securityGroupService, settings)

    String name = createName(settings)
    String userData = getUserData(name, settings, legacyUdf, userDataOverride)
    createLaunchConfiguration(name, userData, settings)
  }

  private static String createDefaultSuffix() {
    new LocalDateTime().toString("MMddYYYYHHmmss")
  }

  static String createName(LaunchConfigurationSettings settings) {
    createName0(settings.baseName, settings.suffix)
  }

  private static String createName0(String baseName, String suffix) {
    StringBuilder name = new StringBuilder(baseName)
    if (suffix) {
      name.append('-').append(suffix)
    }
    name.toString()
  }

  private String getUserData(String launchConfigName, LaunchConfigurationSettings settings, Boolean legacyUdf, UserDataOverride userDataOverride) {
    UserDataInput userDataRequest =
      UserDataInput.builder()
        .launchTemplate(false)
        .asgName(settings.baseName)
        .launchSettingName(launchConfigName)
        .region(settings.region)
        .account(settings.account)
        .accountType(settings.accountType)
        .environment(settings.environment)
        .iamRole(settings.iamRole)
        .imageId(settings.ami)
        .legacyUdf(legacyUdf)
        .userDataOverride(userDataOverride)
        .base64UserData(settings.base64UserData)
        .build()

    return userDataProviderAggregator.aggregate(userDataRequest)
  }

  static LaunchConfigurationSettings setAppSecurityGroup(
    String application,
    String subnetType,
    DeployDefaults deployDefaults,
    SecurityGroupService securityGroupService,
    LaunchConfigurationSettings settings
  ) {
    if (settings.suffix == null) {
      settings = settings.toBuilder().suffix(createDefaultSuffix()).build()
    }

    Set<String> securityGroupIds = securityGroupService.resolveSecurityGroupIdsWithSubnetType(settings.securityGroups, subnetType).toSet()

    if (!securityGroupIds || (deployDefaults.addAppGroupToServerGroup && securityGroupIds.size() < deployDefaults.maxSecurityGroups)) {
      def names = securityGroupService.getSecurityGroupNamesFromIds(securityGroupIds)

      String existingAppGroup = names.keySet().find { it.contains(application) }
      if (!existingAppGroup) {
        OperationPoller.retryWithBackoff({o ->
          String applicationSecurityGroup = securityGroupService.getSecurityGroupForApplication(application, subnetType)
          if (!applicationSecurityGroup) {
            applicationSecurityGroup = securityGroupService.createSecurityGroup(application, subnetType)
          }
          securityGroupIds << applicationSecurityGroup
        }, 500, 3)
      }
    }
    settings = settings.toBuilder().securityGroups(securityGroupIds.toList()).build()

    if (settings.classicLinkVpcSecurityGroups) {
      if (!settings.classicLinkVpcId) {
        throw new IllegalStateException("Can't provide classic link security groups without classiclink vpc Id")
      }
      List<String> classicLinkIds = securityGroupService.resolveSecurityGroupIdsInVpc(settings.classicLinkVpcSecurityGroups, settings.classicLinkVpcId)
      settings = settings.toBuilder().classicLinkVpcSecurityGroups(classicLinkIds).build()
    }

    return settings
  }

  private String createLaunchConfiguration(String name, String userData, LaunchConfigurationSettings settings) {

    CreateLaunchConfigurationRequest request = new CreateLaunchConfigurationRequest()
      .withImageId(settings.ami)
      .withIamInstanceProfile(settings.iamRole)
      .withLaunchConfigurationName(name)
      .withUserData(userData)
      .withInstanceType(settings.instanceType)
      .withSecurityGroups(settings.securityGroups)
      .withKeyName(settings.keyPair)
      .withAssociatePublicIpAddress(settings.associatePublicIpAddress)
      .withKernelId(settings.kernelId ?: null)
      .withRamdiskId(settings.ramdiskId ?: null)
      .withEbsOptimized(settings.ebsOptimized)
      .withSpotPrice(settings.spotPrice)
      .withClassicLinkVPCId(settings.classicLinkVpcId)
      .withClassicLinkVPCSecurityGroups(settings.classicLinkVpcSecurityGroups)
      .withInstanceMonitoring(new InstanceMonitoring(enabled: settings.instanceMonitoring))

    if (settings.blockDevices) {
      def mappings = []
      for (blockDevice in settings.blockDevices) {
        def mapping = new BlockDeviceMapping(deviceName: blockDevice.deviceName)
        if (blockDevice.virtualName) {
          mapping.withVirtualName(blockDevice.virtualName)
        } else {
          def ebs = new Ebs()
          blockDevice.with {
            ebs.withVolumeSize(size)
            if (deleteOnTermination != null) {
              ebs.withDeleteOnTermination(deleteOnTermination)
            }
            if (volumeType) {
              ebs.withVolumeType(volumeType)
            }
            if (iops) {
              ebs.withIops(iops)
            }
            if (snapshotId) {
              ebs.withSnapshotId(snapshotId)
            }
            if (encrypted) {
              ebs.withEncrypted(encrypted)
            }
          }
          mapping.withEbs(ebs)
        }
        mappings << mapping
      }
      request.withBlockDeviceMappings(mappings)
    }

    try {
      OperationPoller.retryWithBackoff({ o ->
        CreateLaunchConfigurationRequest debugRequest = request.clone()
        debugRequest.setUserData(null);
        log.debug("Creating launch configuration (${name}): ${debugRequest}")

        autoScaling.createLaunchConfiguration(request)
      }, 1500, 3);
    } catch (AlreadyExistsException e) {
      log.debug("Launch configuration already exists, continuing... (${e.message})")
    }

    name
  }
}
