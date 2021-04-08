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

package com.netflix.spinnaker.clouddriver.aws.deploy.asg;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.BlockDeviceMapping;
import com.amazonaws.services.autoscaling.model.Ebs;
import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.ec2.model.LaunchTemplateBlockDeviceMapping;
import com.amazonaws.services.ec2.model.LaunchTemplateEbsBlockDevice;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDateTime;

/**
 * A helper class for utility methods related to {@link AutoScalingWorker.AsgConfiguration} and
 * general ASG related configuration.
 */
@Slf4j
public class AsgConfigHelper {

  public static String createName(String baseName, String suffix) {
    StringBuilder name = new StringBuilder(baseName);
    if (StringUtils.isNotEmpty(suffix)) {
      name.append('-').append(suffix);
    } else {
      name.append('-').append(createDefaultSuffix());
    }
    return name.toString();
  }

  /**
   * Set resolved security groups for an application.
   *
   * @param asgConfig Asg Configuration to work with
   * @param securityGroupService SecurityGroup service
   * @param deployDefaults defaults
   * @return asgConfig with resolved security groups and classicLinkVpcSecurityGroups
   */
  public static AutoScalingWorker.AsgConfiguration setAppSecurityGroups(
      AutoScalingWorker.AsgConfiguration asgConfig,
      SecurityGroupService securityGroupService,
      DeployDefaults deployDefaults) {

    // resolve security group ids and names in request
    List<String> securityGroupIds =
        securityGroupService.resolveSecurityGroupIdsWithSubnetType(
            asgConfig.getSecurityGroups(), asgConfig.getSubnetType());

    // conditionally, find or create an application security group
    if ((securityGroupIds == null || securityGroupIds.isEmpty())
        || (deployDefaults.isAddAppGroupToServerGroup()
            && securityGroupIds.size() < deployDefaults.getMaxSecurityGroups())) {

      // get a mapping of security group names to ids and find an existing security group for
      // application
      final Map<String, String> names =
          securityGroupService.getSecurityGroupNamesFromIds(securityGroupIds);
      String existingAppGroup =
          (names != null && !names.isEmpty())
              ? names.keySet().stream()
                  .filter(it -> it.contains(asgConfig.getApplication()))
                  .findFirst()
                  .orElse(null)
              : null;

      // if no existing security group, find by subnet type / create a new security group for
      // application
      if (StringUtils.isEmpty(existingAppGroup)) {
        String applicationSecurityGroupId =
            (String)
                OperationPoller.retryWithBackoff(
                    o ->
                        createSecurityGroupForApp(
                            securityGroupService,
                            asgConfig.getApplication(),
                            asgConfig.getSubnetType()),
                    500,
                    3);
        securityGroupIds.add(applicationSecurityGroupId);
      }
    }
    asgConfig.setSecurityGroups(securityGroupIds.stream().distinct().collect(Collectors.toList()));

    if (asgConfig.getClassicLinkVpcSecurityGroups() != null
        && !asgConfig.getClassicLinkVpcSecurityGroups().isEmpty()) {
      if (StringUtils.isEmpty(asgConfig.getClassicLinkVpcId())) {
        throw new IllegalStateException(
            "Can't provide classic link security groups without classiclink vpc Id");
      }
      List<String> classicLinkIds =
          securityGroupService.resolveSecurityGroupIdsInVpc(
              asgConfig.getClassicLinkVpcSecurityGroups(), asgConfig.getClassicLinkVpcId());
      asgConfig.setClassicLinkVpcSecurityGroups(classicLinkIds);
    }

    log.info(
        "Configured resolved security groups {} for application {}.",
        securityGroupIds,
        asgConfig.getApplication());
    return asgConfig;
  }

  /**
   * Get block device mappings specified in an ASG.
   *
   * @param asg AWS AutoScalingGroup
   * @param asgRegionScopedProvider regionScopedProvider for the asg
   * @return a list of AmazonBlockDevice indicating the block device mapping specified in the asg
   * @throws IllegalStateException if certain AWS entities are not found / show error conditions.
   */
  public static List<AmazonBlockDevice> getBlockDeviceMappingForAsg(
      final AutoScalingGroup asg,
      RegionScopedProviderFactory.RegionScopedProvider asgRegionScopedProvider) {
    if (asg.getLaunchConfigurationName() != null) {
      final LaunchConfiguration lc =
          asgRegionScopedProvider
              .getAsgService()
              .getLaunchConfiguration(asg.getLaunchConfigurationName());
      if (lc == null) {
        throw new IllegalStateException(
            "Launch configuration "
                + asg.getLaunchConfigurationName()
                + " was requested but was not found for ASG with launch configuration "
                + asg.getAutoScalingGroupName());
      }
      return transformBlockDeviceMapping(lc.getBlockDeviceMappings());
    } else if (asg.getLaunchTemplate() != null) {
      final LaunchTemplateVersion ltVersion =
          asgRegionScopedProvider
              .getLaunchTemplateService()
              .getLaunchTemplateVersion(asg.getLaunchTemplate())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Launch template "
                              + asg.getLaunchTemplate()
                              + " was requested but was not found for ASG with launch template "
                              + asg.getAutoScalingGroupName()));
      return transformLaunchTemplateBlockDeviceMapping(
          ltVersion.getLaunchTemplateData().getBlockDeviceMappings());
    } else if (asg.getMixedInstancesPolicy() != null) {
      final LaunchTemplateSpecification ltSpec =
          asg.getMixedInstancesPolicy().getLaunchTemplate().getLaunchTemplateSpecification();
      final LaunchTemplateVersion ltVersion =
          asgRegionScopedProvider
              .getLaunchTemplateService()
              .getLaunchTemplateVersion(ltSpec)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Launch template "
                              + ltSpec
                              + " was requested but was not found for ASG with mixed instances policy "
                              + asg.getAutoScalingGroupName()));
      return transformLaunchTemplateBlockDeviceMapping(
          ltVersion.getLaunchTemplateData().getBlockDeviceMappings());
    } else {
      throw new IllegalStateException(
          String.format(
              "An AWS ASG %s is expected to have a launch configuration or launch template or mixed instances policy",
              asg.getAutoScalingGroupName()));
    }
  }

  /**
   * Get instance types specified in an ASG.
   *
   * @param asg AWS AutoScalingGroup
   * @param asgRegionScopedProvider regionScopedProvider for the asg
   * @return a list of instance types with a single type for launch configuration / launch template
   *     and multiple types for mixed instances policy backed ASG
   * @throws IllegalStateException if certain AWS entities are not found / show error conditions.
   */
  public static Set<String> getAllowedInstanceTypesForAsg(
      final AutoScalingGroup asg,
      RegionScopedProviderFactory.RegionScopedProvider asgRegionScopedProvider) {
    if (asg.getMixedInstancesPolicy() != null
        && asg.getMixedInstancesPolicy().getLaunchTemplate().getOverrides() != null) {
      return asg.getMixedInstancesPolicy().getLaunchTemplate().getOverrides().stream()
          .map(override -> override.getInstanceType())
          .collect(Collectors.toSet());
    } else {
      return Collections.singleton(getTopLevelInstanceTypeForAsg(asg, asgRegionScopedProvider));
    }
  }

  /**
   * Get the top-level instance type specified in an ASG. For the case of multiple instance types,
   * top-level instance type is the one specified in the launch template (NOT overrides). The
   * top-level instance type in an ASG is nothing but the {@link
   * BasicAmazonDeployDescription#getInstanceType()}
   *
   * @param asg asg AWS AutoScalingGroup
   * @param asgRegionScopedProvider regionScopedProvider for the asg
   * @return a single instance type which corresponds to {@link
   *     BasicAmazonDeployDescription#getInstanceType()}
   * @throws IllegalStateException if certain AWS entities are not found / show error conditions.
   */
  public static String getTopLevelInstanceTypeForAsg(
      final AutoScalingGroup asg,
      RegionScopedProviderFactory.RegionScopedProvider asgRegionScopedProvider) {
    if (asg.getLaunchConfigurationName() != null) {
      final LaunchConfiguration lc =
          asgRegionScopedProvider
              .getAsgService()
              .getLaunchConfiguration(asg.getLaunchConfigurationName());
      if (lc == null) {
        throw new IllegalStateException(
            String.format(
                "Launch configuration %s was requested but was not found for ASG with launch configuration %s.",
                asg.getLaunchConfigurationName(), asg.getAutoScalingGroupName()));
      }
      return lc.getInstanceType();
    } else if (asg.getLaunchTemplate() != null) {
      final LaunchTemplateVersion ltVersion =
          asgRegionScopedProvider
              .getLaunchTemplateService()
              .getLaunchTemplateVersion(asg.getLaunchTemplate())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "Launch template %s was requested but was not found for ASG with launch template %s.",
                              asg.getLaunchTemplate(), asg.getAutoScalingGroupName())));

      return ltVersion.getLaunchTemplateData().getInstanceType();
    } else if (asg.getMixedInstancesPolicy() != null) {
      final LaunchTemplateSpecification ltSpec =
          asg.getMixedInstancesPolicy().getLaunchTemplate().getLaunchTemplateSpecification();
      final LaunchTemplateVersion ltVersion =
          asgRegionScopedProvider
              .getLaunchTemplateService()
              .getLaunchTemplateVersion(ltSpec)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "Launch template %s was requested but was not found for ASG with mixed instances policy %s.",
                              ltSpec, asg.getAutoScalingGroupName())));

      return ltVersion.getLaunchTemplateData().getInstanceType();
    } else {
      throw new IllegalStateException(
          "An AWS ASG is expected to include a launch configuration or launch template or mixed instances policy "
              + "but neither was found in ASG "
              + asg);
    }
  }

  private static String createSecurityGroupForApp(
      SecurityGroupService securityGroupService, String application, String subnetType) {

    // find security group by subnet type
    String applicationSecurityGroupId =
        securityGroupService.getSecurityGroupForApplication(application, subnetType);

    // conditionally, create security group
    if (StringUtils.isEmpty(applicationSecurityGroupId)) {
      log.debug("Creating security group for application {}", application);
      applicationSecurityGroupId =
          securityGroupService.createSecurityGroup(application, subnetType);
    }

    return applicationSecurityGroupId;
  }

  private static String createDefaultSuffix() {
    return new LocalDateTime().toString("MMddYYYYHHmmss");
  }

  /**
   * Transform AWS BlockDeviceMapping (found in ASG LaunchConfiguration) to {@link
   * AmazonBlockDevice}. Used while extracting launch settings from AWS AutoScalingGroup.
   *
   * @param blockDeviceMappings AWS BlockDeviceMappings
   * @return list of AmazonBlockDevice
   */
  protected static List<AmazonBlockDevice> transformBlockDeviceMapping(
      List<BlockDeviceMapping> blockDeviceMappings) {
    return blockDeviceMappings.stream()
        .map(
            bdm -> {
              AmazonBlockDevice amzBd =
                  new AmazonBlockDevice.Builder()
                      .deviceName(bdm.getDeviceName())
                      .virtualName(bdm.getVirtualName())
                      .build();

              if (bdm.getEbs() != null) {
                final Ebs ebs = bdm.getEbs();
                amzBd.setIops(ebs.getIops());
                amzBd.setDeleteOnTermination(ebs.getDeleteOnTermination());
                amzBd.setSize(ebs.getVolumeSize());
                amzBd.setVolumeType(ebs.getVolumeType());
                amzBd.setSnapshotId(ebs.getSnapshotId());
                if (ebs.getSnapshotId() == null) {
                  // only set encryption if snapshotId isn't provided. AWS will error out otherwise
                  amzBd.setEncrypted(ebs.getEncrypted());
                }
              }
              return amzBd;
            })
        .collect(Collectors.toList());
  }

  /**
   * Transform AWS BlockDeviceMapping (found in EC2 LaunchTemplate) to {@link AmazonBlockDevice}.
   * Used while extracting launch settings from AWS AutoScalingGroup.
   *
   * @param launchTemplateBlockDeviceMappings AWS LaunchTemplate BlockDeviceMappings
   * @return list of AmazonBlockDevice
   */
  protected static List<AmazonBlockDevice> transformLaunchTemplateBlockDeviceMapping(
      List<LaunchTemplateBlockDeviceMapping> launchTemplateBlockDeviceMappings) {
    return launchTemplateBlockDeviceMappings.stream()
        .map(
            ltBdm -> {
              AmazonBlockDevice amzBd =
                  new AmazonBlockDevice.Builder()
                      .deviceName(ltBdm.getDeviceName())
                      .virtualName(ltBdm.getVirtualName())
                      .build();

              if (ltBdm.getEbs() != null) {
                final LaunchTemplateEbsBlockDevice ebs = ltBdm.getEbs();
                amzBd.setIops(ebs.getIops());
                amzBd.setDeleteOnTermination(ebs.getDeleteOnTermination());
                amzBd.setSize(ebs.getVolumeSize());
                amzBd.setVolumeType(ebs.getVolumeType());
                amzBd.setSnapshotId(ebs.getSnapshotId());
                if (ebs.getSnapshotId() == null) {
                  // only set encryption if snapshotId isn't provided. AWS will error out otherwise
                  amzBd.setEncrypted(ebs.getEncrypted());
                }
              }
              return amzBd;
            })
        .collect(Collectors.toList());
  }
}
