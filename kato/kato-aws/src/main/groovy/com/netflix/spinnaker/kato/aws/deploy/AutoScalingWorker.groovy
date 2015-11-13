/*
 * Copyright 2014 Netflix, Inc.
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
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.SuspendProcessesRequest
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.amazonaws.services.ec2.model.DescribeSubnetsResult
import com.amazonaws.services.ec2.model.Subnet
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.kato.aws.deploy.userdata.UserDataProvider
import com.netflix.spinnaker.kato.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.kato.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.kato.aws.model.SubnetData
import com.netflix.spinnaker.kato.aws.model.SubnetTarget
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
/**
 * A worker class dedicated to the deployment of "applications", following many of Netflix's common AWS conventions.
 *
 *
 */
class AutoScalingWorker {
  private static final String AWS_PHASE = "AWS_DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private String application
  private String region
  private NetflixAmazonCredentials credentials
  private String stack
  private String freeFormDetails
  private String ami
  private String classicLinkVpcId
  private String instanceType
  private String iamRole
  private String keyPair
  private Boolean ignoreSequence
  private Boolean startDisabled
  private Boolean associatePublicIpAddress
  private String subnetType
  private Integer cooldown
  private Integer healthCheckGracePeriod
  private String healthCheckType
  private String spotPrice
  private Collection<String> suspendedProcesses
  private Collection<String> terminationPolicies
  private String kernelId
  private String ramdiskId
  private Boolean instanceMonitoring
  private Boolean ebsOptimized
  private List<String> loadBalancers
  private List<String> securityGroups
  private List<String> availabilityZones
  private List<AmazonBlockDevice> blockDevices

  private int minInstances
  private int maxInstances
  private int desiredInstances

  private RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider

  AutoScalingWorker() {

  }

  /**
   * Initiates the activity of deployment. This will involve:
   *  <ol>
   *    <li>Lookup or create if not found, a security group with a name that matches the supplied "application";</li>
   *    <li>Looking up security group ids for the names provided as "securityGroups";</li>
   *    <li>Look up an ancestor ASG based on Netflix naming conventions, and bring its security groups to the new ASG;</li>
   *    <li>Retrieve user data from all available {@link UserDataProvider}s;</li>
   *    <li>Create the ASG's Launch Configuration with User Data and Security Groups;</li>
   *    <li>Create a new ASG in the subnets found from the optionally supplied subnetType.</li>
   *  </ol>
   *
   * @return the name of the newly deployed ASG
   */
  String deploy() {
    task.updateStatus AWS_PHASE, "Beginning Amazon deployment."

    if (startDisabled) {
      suspendedProcesses.addAll(AutoScalingProcessType.getDisableProcesses()*.name())
    }

    task.updateStatus AWS_PHASE, "Beginning ASG deployment."

    AWSServerGroupNameResolver awsServerGroupNameResolver = regionScopedProvider.AWSServerGroupNameResolver
    String asgName = awsServerGroupNameResolver.resolveNextServerGroupName(application, stack, freeFormDetails, ignoreSequence)

    def settings = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
      account: credentials.name,
      environment: credentials.environment,
      accountType: credentials.accountType,
      region: region,
      baseName: asgName,
      suffix: null,
      ami: ami,
      iamRole: iamRole,
      classicLinkVpcId: classicLinkVpcId,
      instanceType: instanceType,
      keyPair: keyPair,
      associatePublicIpAddress: associatePublicIpAddress,
      kernelId: kernelId,
      ramdiskId: ramdiskId,
      ebsOptimized: ebsOptimized,
      spotPrice: spotPrice,
      instanceMonitoring: instanceMonitoring,
      blockDevices: blockDevices,
      securityGroups: securityGroups)

    String launchConfigName = regionScopedProvider.getLaunchConfigurationBuilder().buildLaunchConfiguration(application, subnetType, settings)

    task.updateStatus AWS_PHASE, "Deploying ASG."

    createAutoScalingGroup(asgName, launchConfigName)
  }

  /**
   * This is an obscure rule that Subnets are tagged at Amazon with a data structure, which defines their purpose and
   * what type of resources (elb or ec2) are able to make use of them. We also need to ensure that the Subnet IDs that
   * we provide back are able to be deployed to based off of the supplied availability zones.
   *
   * @return list of subnet ids applicable to this deployment.
   */
  List<String> getSubnetIds() {
    subnetType ? getSubnets().subnetId : []
  }

  private List<Subnet> getSubnets() {
    DescribeSubnetsResult result = regionScopedProvider.amazonEC2.describeSubnets()
    List<Subnet> mySubnets = []
    for (subnet in result.subnets) {
      if (availabilityZones && !availabilityZones.contains(subnet.availabilityZone)) {
        continue
      }
      SubnetData sd = SubnetData.from(subnet)
      if (sd.purpose == subnetType && (sd.target == null || sd.target == SubnetTarget.EC2)) {
        mySubnets << subnet
      }
    }
    mySubnets
  }

  /**
   * Deploys a new ASG with as much data collected as possible.
   *
   * @param asgName
   * @param launchConfigurationName
   * @return
   */
  String createAutoScalingGroup(String asgName, String launchConfigurationName) {
    CreateAutoScalingGroupRequest request = new CreateAutoScalingGroupRequest()
      .withAutoScalingGroupName(asgName)
      .withLaunchConfigurationName(launchConfigurationName)
      .withMinSize(0)
      .withMaxSize(0)
      .withDesiredCapacity(0)
      .withLoadBalancerNames(loadBalancers)
      .withDefaultCooldown(cooldown)
      .withHealthCheckGracePeriod(healthCheckGracePeriod)
      .withHealthCheckType(healthCheckType)
      .withTerminationPolicies(terminationPolicies)

    // Favor subnetIds over availability zones
    def subnetIds = subnetIds?.join(',')
    if (subnetIds) {
      task.updateStatus AWS_PHASE, " > Deploying to subnetIds: $subnetIds"
      request.withVPCZoneIdentifier(subnetIds)
    } else if (subnetType && !subnets) {
      throw new RuntimeException("No suitable subnet was found for internal subnet purpose '${subnetType}'!")
    } else {
      task.updateStatus AWS_PHASE, "Deploying to availabilityZones: $availabilityZones"
      request.withAvailabilityZones(availabilityZones)
    }

    def autoScaling = regionScopedProvider.autoScaling
    autoScaling.createAutoScalingGroup(request)
    if (suspendedProcesses) {
      autoScaling.suspendProcesses(new SuspendProcessesRequest(autoScalingGroupName: asgName, scalingProcesses: suspendedProcesses))
    }
    autoScaling.updateAutoScalingGroup(new UpdateAutoScalingGroupRequest(autoScalingGroupName: asgName,
      minSize: minInstances, maxSize: maxInstances, desiredCapacity: desiredInstances))

    asgName
  }
}
