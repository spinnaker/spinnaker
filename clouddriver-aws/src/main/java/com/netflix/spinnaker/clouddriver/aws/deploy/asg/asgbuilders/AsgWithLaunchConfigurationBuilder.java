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

package com.netflix.spinnaker.clouddriver.aws.deploy.asg.asgbuilders;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgLifecycleHookWorker;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AutoScalingWorker.AsgConfiguration;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.LaunchConfigurationBuilder;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import lombok.extern.slf4j.Slf4j;

/** A builder used to build an AWS Autoscaling group with launch configuration. */
@Slf4j
public class AsgWithLaunchConfigurationBuilder extends AsgBuilder {
  private LaunchConfigurationBuilder lcBuilder;

  public AsgWithLaunchConfigurationBuilder(
      LaunchConfigurationBuilder lcBuilder,
      AmazonAutoScaling autoScaling,
      AmazonEC2 ec2,
      AsgLifecycleHookWorker asgLifecycleHookWorker) {
    super(autoScaling, ec2, asgLifecycleHookWorker);

    this.lcBuilder = lcBuilder;
  }

  @Override
  protected CreateAutoScalingGroupRequest buildRequest(
      Task task, String taskPhase, String asgName, AsgConfiguration cfg) {

    // create LC settings
    LaunchConfigurationBuilder.LaunchConfigurationSettings settings =
        LaunchConfigurationBuilder.LaunchConfigurationSettings.builder()
            .account(cfg.getCredentials().getName())
            .environment(cfg.getCredentials().getEnvironment())
            .accountType(cfg.getCredentials().getAccountType())
            .region(cfg.getRegion())
            .baseName(asgName)
            .suffix(null)
            .ami(cfg.getAmi())
            .iamRole(cfg.getIamRole())
            .classicLinkVpcId(cfg.getClassicLinkVpcId())
            .classicLinkVpcSecurityGroups(cfg.getClassicLinkVpcSecurityGroups())
            .instanceType(cfg.getInstanceType())
            .keyPair(cfg.getKeyPair())
            .base64UserData(cfg.getBase64UserData())
            .associatePublicIpAddress(cfg.getAssociatePublicIpAddress())
            .kernelId(cfg.getKernelId())
            .ramdiskId(cfg.getRamdiskId())
            .ebsOptimized(cfg.getEbsOptimized() != null ? cfg.getEbsOptimized() : false)
            .spotPrice(cfg.getSpotMaxPrice())
            .instanceMonitoring(
                cfg.getInstanceMonitoring() != null ? cfg.getInstanceMonitoring() : false)
            .blockDevices(cfg.getBlockDevices())
            .securityGroups(cfg.getSecurityGroups())
            .build();

    String launchConfigName =
        lcBuilder.buildLaunchConfiguration(
            cfg.getApplication(),
            cfg.getSubnetType(),
            settings,
            cfg.getLegacyUdf(),
            cfg.getUserDataOverride());

    task.updateStatus(
        taskPhase, "Deploying ASG " + asgName + " with launch configuration " + launchConfigName);
    CreateAutoScalingGroupRequest request = buildPartialRequest(task, taskPhase, asgName, cfg);

    return request.withLaunchConfigurationName(launchConfigName);
  }
}
