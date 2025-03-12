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
import com.amazonaws.services.autoscaling.model.InstancesDistribution;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.autoscaling.model.MixedInstancesPolicy;
import com.amazonaws.services.ec2.AmazonEC2;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgConfigHelper;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgLifecycleHookWorker;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AutoScalingWorker.AsgConfiguration;
import com.netflix.spinnaker.clouddriver.aws.services.LaunchTemplateService;
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults;
import lombok.extern.slf4j.Slf4j;

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
      AmazonAutoScaling autoScaling,
      AmazonEC2 ec2,
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
    final com.amazonaws.services.ec2.model.LaunchTemplate ec2Lt =
        ec2LtService.createLaunchTemplate(
            config, asgName, AsgConfigHelper.createName(asgName, null));

    // create ASG LaunchTemplate spec
    LaunchTemplateSpecification asgLtSpec =
        new LaunchTemplateSpecification()
            .withLaunchTemplateId(ec2Lt.getLaunchTemplateId())
            .withVersion("$Latest");

    // create ASG LaunchTemplate
    com.amazonaws.services.autoscaling.model.LaunchTemplate asgLt =
        new com.amazonaws.services.autoscaling.model.LaunchTemplate()
            .withLaunchTemplateSpecification(asgLtSpec);

    // create and add overrides
    // https://docs.aws.amazon.com/autoscaling/ec2/userguide/asg-override-options.html
    asgLt.withOverrides(
        AsgConfigHelper.getLaunchTemplateOverrides(
            config.getLaunchTemplateOverridesForInstanceType()));

    // configure instance distribution
    // https://docs.aws.amazon.com/autoscaling/ec2/userguide/asg-purchase-options.html
    InstancesDistribution dist =
        new InstancesDistribution()
            .withOnDemandBaseCapacity(config.getOnDemandBaseCapacity())
            .withOnDemandPercentageAboveBaseCapacity(
                config.getOnDemandPercentageAboveBaseCapacity())
            .withSpotInstancePools(config.getSpotInstancePools())
            .withSpotMaxPrice(config.getSpotMaxPrice())
            .withSpotAllocationStrategy(config.getSpotAllocationStrategy());

    // create mixed instances policy with overrides and instance distribution
    final MixedInstancesPolicy mixedInsPolicy =
        new MixedInstancesPolicy().withLaunchTemplate(asgLt).withInstancesDistribution(dist);

    task.updateStatus(
        taskPhase,
        "Deploying ASG " + asgName + " with mixed instances policy " + mixedInsPolicy.toString());
    CreateAutoScalingGroupRequest request = buildPartialRequest(task, taskPhase, asgName, config);

    return request.withMixedInstancesPolicy(mixedInsPolicy);
  }
}
