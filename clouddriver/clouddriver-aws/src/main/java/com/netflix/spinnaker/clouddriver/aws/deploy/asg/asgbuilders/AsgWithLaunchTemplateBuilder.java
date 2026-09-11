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

import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgConfigHelper;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgLifecycleHookWorker;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AutoScalingWorker.AsgConfiguration;
import com.netflix.spinnaker.clouddriver.aws.services.LaunchTemplateService;
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.CreateAutoScalingGroupRequest;
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.LaunchTemplate;

/** A builder used to build an AWS Autoscaling group with launch template. */
@Slf4j
public class AsgWithLaunchTemplateBuilder extends AsgBuilder {
  private LaunchTemplateService ltService;
  private SecurityGroupService securityGroupService;
  private DeployDefaults deployDefaults;

  public AsgWithLaunchTemplateBuilder(
      LaunchTemplateService ltService,
      SecurityGroupService securityGroupService,
      DeployDefaults deployDefaults,
      AutoScalingClient autoScaling,
      Ec2Client ec2,
      AsgLifecycleHookWorker asgLifecycleHookWorker) {
    super(autoScaling, ec2, asgLifecycleHookWorker);

    this.ltService = ltService;
    this.securityGroupService = securityGroupService;
    this.deployDefaults = deployDefaults;
  }

  @Override
  protected CreateAutoScalingGroupRequest buildRequest(
      Task task, String taskPhase, String asgName, AsgConfiguration config) {

    // resolve security groups
    config = AsgConfigHelper.setAppSecurityGroups(config, securityGroupService, deployDefaults);

    final LaunchTemplate lt =
        ltService.createLaunchTemplate(config, asgName, AsgConfigHelper.createName(asgName, null));

    final LaunchTemplateSpecification ltSpec =
        LaunchTemplateSpecification.builder()
            .launchTemplateId(lt.launchTemplateId())
            .version(lt.latestVersionNumber().toString())
            .build();

    task.updateStatus(
        taskPhase, "Deploying ASG " + asgName + " with launch template " + lt.launchTemplateId());
    CreateAutoScalingGroupRequest request = buildPartialRequest(task, taskPhase, asgName, config);

    return request.toBuilder().launchTemplate(ltSpec).build();
  }
}
