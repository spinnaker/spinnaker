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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.autoscaling.model.DisableMetricsCollectionRequest
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.netflix.frigga.Names
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.clouddriver.aws.deploy.AmiIdResolver
import com.netflix.spinnaker.clouddriver.aws.deploy.BlockDeviceConfig
import com.netflix.spinnaker.clouddriver.aws.deploy.ResolvedAmiResult
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyAsgLaunchConfigurationDescription
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class ModifyAsgLaunchConfigurationOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "MODIFY_ASG_LAUNCH_CONFIGURATION"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Autowired
  AwsConfiguration.DeployDefaults deployDefaults

  @Autowired
  BlockDeviceConfig blockDeviceConfig

  private final ModifyAsgLaunchConfigurationDescription description

  ModifyAsgLaunchConfigurationOperation(ModifyAsgLaunchConfigurationDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing operation..."
    def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, description.region)
    def lcBuilder = regionScopedProvider.launchConfigurationBuilder

    def asg = regionScopedProvider.asgService.getAutoScalingGroup(description.asgName)
    def existingLc = asg.launchConfigurationName

    def settings = lcBuilder.buildSettingsFromLaunchConfiguration(description.credentials, description.region, existingLc)

    def props = [:]
    if (!asg.getVPCZoneIdentifier() && !settings.classicLinkVpcId) {
      def classicLinkVpc = regionScopedProvider.amazonEC2.describeVpcClassicLink().vpcs.find { it.classicLinkEnabled }
      if (classicLinkVpc) {
        props.classicLinkVpcId = classicLinkVpc.vpcId
        if (deployDefaults.classicLinkSecurityGroupName) {
          props.classicLinkVpcSecurityGroups = [ deployDefaults.classicLinkSecurityGroupName ]
        }
      }
    }

    def settingsKeys = settings.properties.keySet()
    props = props + description.properties.findResults { k, v -> (v != null && settingsKeys.contains(k)) ? [k, v] : null }.collectEntries()
    props.remove('class')

    if (props.spotPrice == "") {
      // a spotPrice of "" indicates that it should be removed regardless of value on source launch configuration
      props.spotPrice = null
    }

    if (description.amiName) {
      def amazonEC2 = regionScopedProvider.amazonEC2
      ResolvedAmiResult ami = priorOutputs.find({
        it instanceof ResolvedAmiResult && it.region == description.region && it.amiName == description.amiName
      }) ?: AmiIdResolver.resolveAmiIdFromAllSources(amazonEC2, description.region, description.amiName, description.credentials.accountId)

      props.ami = ami.amiId
    }

    if (description.securityGroupsAppendOnly) {
      props.securityGroups = settings.securityGroups + description.securityGroups
    }

    //if we are changing instance types and don't have explicitly supplied block device mappings
    if (!description.blockDevices && description.instanceType != null && description.instanceType != settings.instanceType) {
      if (!description.copySourceCustomBlockDeviceMappings) {
        props.blockDevices = blockDeviceConfig.getBlockDevicesForInstanceType(description.instanceType)
      } else {
        def blockDevicesForSourceLaunchConfig = settings.blockDevices.collect {
          [deviceName: it.deviceName, virtualName: it.virtualName, size: it.size]
        }.sort { it.deviceName }
        def blockDevicesForSourceInstanceType = blockDeviceConfig.getBlockDevicesForInstanceType(
            settings.instanceType
        ).collect {
          [deviceName: it.deviceName, virtualName: it.virtualName, size: it.size]
        }.sort { it.deviceName }

        if (blockDevicesForSourceLaunchConfig == blockDevicesForSourceInstanceType) {
          // use default block mappings for the new instance type (since default block mappings were used on the previous instance type)
          props.blockDevices = blockDeviceConfig.getBlockDevicesForInstanceType(description.instanceType)
        }
      }
    }

    def newSettings = settings.copyWith(props)

    if (newSettings == settings && description.legacyUdf == null) {
      task.updateStatus BASE_PHASE, "No changes required for launch configuration on $description.asgName in $description.region"
    } else {
      newSettings = newSettings.copyWith(suffix: null)
      def name = Names.parseName(description.asgName)
      def newLc = lcBuilder.buildLaunchConfiguration(name.app, description.subnetType, newSettings, description.legacyUdf)

      def autoScaling = regionScopedProvider.autoScaling

      if (!newSettings.instanceMonitoring && settings.instanceMonitoring) {
        autoScaling.disableMetricsCollection(
          new DisableMetricsCollectionRequest()
            .withAutoScalingGroupName(description.asgName))
      }

      autoScaling.updateAutoScalingGroup(
        new UpdateAutoScalingGroupRequest()
          .withAutoScalingGroupName(description.asgName)
          .withLaunchConfigurationName(newLc))
    }

    task.updateStatus BASE_PHASE, "completed for $description.asgName in $description.region."
    null
  }

}
