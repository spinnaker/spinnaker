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
import software.amazon.awssdk.services.autoscaling.model.InstancesDistribution;
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification;
import software.amazon.awssdk.services.autoscaling.model.MixedInstancesPolicy;
import software.amazon.awssdk.services.ec2.Ec2Client;

/**
 * A builder used to build an AWS Autoscaling group with mixed instances policy, backed by EC2
 * launch template. https://docs.aws.amazon.com/autoscaling/ec2/userguide/asg-purchase-options.html
 */
@Slf4j
public class AsgWithMixedInstancesPolicyBuilder extends AsgBuilder {
  private LaunchTemplateService ec2LtService;
  private SecurityGroupService securityGroupService;
  private DeployDefaults deployDefaults;

  public AsgWithMixedInstancesPolicyBuilder(
      LaunchTemplateService ec2LtService,
      SecurityGroupService securityGroupService,
      DeployDefaults deployDefaults,
      AutoScalingClient autoScaling,
      Ec2Client ec2,
      AsgLifecycleHookWorker asgLifecycleHookWorker) {
    super(autoScaling, ec2, asgLifecycleHookWorker);

    this.securityGroupService = securityGroupService;
    this.deployDefaults = deployDefaults;
    this.ec2LtService = ec2LtService;
  }

  @Override
  public CreateAutoScalingGroupRequest buildRequest(
      Task task, String taskPhase, String asgName, AsgConfiguration config) {

    // resolve security groups
    config = AsgConfigHelper.setAppSecurityGroups(config, securityGroupService, deployDefaults);

    // create EC2 LaunchTemplate
    final software.amazon.awssdk.services.ec2.model.LaunchTemplate ec2Lt =
        ec2LtService.createLaunchTemplate(
            config, asgName, AsgConfigHelper.createName(asgName, null));

    // create ASG LaunchTemplate spec
    LaunchTemplateSpecification asgLtSpec =
        LaunchTemplateSpecification.builder()
            .launchTemplateId(ec2Lt.launchTemplateId())
            .version("$Latest")
            .build();

    // create and add overrides
    // https://docs.aws.amazon.com/autoscaling/ec2/userguide/asg-override-options.html
    // create ASG LaunchTemplate
    software.amazon.awssdk.services.autoscaling.model.LaunchTemplate asgLt =
        software.amazon.awssdk.services.autoscaling.model.LaunchTemplate.builder()
            .launchTemplateSpecification(asgLtSpec)
            .overrides(
                AsgConfigHelper.getLaunchTemplateOverrides(
                    config.getLaunchTemplateOverridesForInstanceType()))
            .build();

    // configure instance distribution
    // https://docs.aws.amazon.com/autoscaling/ec2/userguide/asg-purchase-options.html
    InstancesDistribution dist =
        InstancesDistribution.builder()
            .onDemandBaseCapacity(config.getOnDemandBaseCapacity())
            .onDemandPercentageAboveBaseCapacity(config.getOnDemandPercentageAboveBaseCapacity())
            .spotInstancePools(config.getSpotInstancePools())
            .spotMaxPrice(config.getSpotMaxPrice())
            .spotAllocationStrategy(config.getSpotAllocationStrategy())
            .build();

    // create mixed instances policy with overrides and instance distribution
    final MixedInstancesPolicy mixedInsPolicy =
        MixedInstancesPolicy.builder().launchTemplate(asgLt).instancesDistribution(dist).build();

    task.updateStatus(
        taskPhase,
        "Deploying ASG " + asgName + " with mixed instances policy " + mixedInsPolicy.toString());
    CreateAutoScalingGroupRequest request = buildPartialRequest(task, taskPhase, asgName, config);

    return request.toBuilder().mixedInstancesPolicy(mixedInsPolicy).build();
  }
}
