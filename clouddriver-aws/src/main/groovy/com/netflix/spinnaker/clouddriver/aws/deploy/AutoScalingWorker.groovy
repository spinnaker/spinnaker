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

package com.netflix.spinnaker.clouddriver.aws.deploy

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AlreadyExistsException
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.EnableMetricsCollectionRequest
import com.amazonaws.services.autoscaling.model.SuspendProcessesRequest
import com.amazonaws.services.autoscaling.model.Tag
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.amazonaws.services.ec2.model.DescribeSubnetsResult
import com.amazonaws.services.ec2.model.Subnet
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.clouddriver.aws.model.SubnetData
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.kork.core.RetrySupport
import groovy.util.logging.Slf4j

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.function.Supplier

/**
 * A worker class dedicated to the deployment of "applications", following many of Netflix's common AWS conventions.
 *
 *
 */
@Slf4j
class AutoScalingWorker {
  private static final String AWS_PHASE = "AWS_DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final RetrySupport retrySupport = new RetrySupport()

  private String application
  private String region
  private NetflixAmazonCredentials credentials
  private String stack
  private String freeFormDetails
  private String ami
  private String classicLinkVpcId
  private List<String> classicLinkVpcSecurityGroups
  private String instanceType
  private String iamRole
  private String keyPair
  private String base64UserData
  private Boolean legacyUdf
  private Integer sequence
  private Boolean ignoreSequence
  private Boolean startDisabled
  private Boolean associatePublicIpAddress
  private String subnetType
  private List<String> subnetIds
  private Integer cooldown
  private Collection<String> enabledMetrics
  private Integer healthCheckGracePeriod
  private String healthCheckType
  private String spotPrice
  private Set<String> suspendedProcesses
  private Collection<String> terminationPolicies
  private String kernelId
  private String ramdiskId
  private Boolean instanceMonitoring
  private Boolean ebsOptimized
  private Collection<String> classicLoadBalancers
  private Collection<String> targetGroupArns
  private List<String> securityGroups
  private List<String> availabilityZones
  private List<AmazonBlockDevice> blockDevices
  private Map<String, String> tags

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
   *    <li>Retrieve user data from all available {@link com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProvider}s;</li>
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
    String asgName
    if (sequence != null) {
      asgName = awsServerGroupNameResolver.generateServerGroupName(application, stack, freeFormDetails, sequence, false)
    }  else {
      asgName = awsServerGroupNameResolver.resolveNextServerGroupName(application, stack, freeFormDetails, ignoreSequence)
    }

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
      classicLinkVpcSecurityGroups: classicLinkVpcSecurityGroups,
      instanceType: instanceType,
      keyPair: keyPair,
      base64UserData: base64UserData?.trim(),
      associatePublicIpAddress: associatePublicIpAddress,
      kernelId: kernelId,
      ramdiskId: ramdiskId,
      ebsOptimized: ebsOptimized,
      spotPrice: spotPrice,
      instanceMonitoring: instanceMonitoring,
      blockDevices: blockDevices,
      securityGroups: securityGroups)

    String launchConfigName = regionScopedProvider.getLaunchConfigurationBuilder().buildLaunchConfiguration(application, subnetType, settings, legacyUdf)

    task.updateStatus AWS_PHASE, "Deploying ASG: $asgName"

    createAutoScalingGroup(asgName, launchConfigName)
  }

  /**
   * This is an obscure rule that Subnets are tagged at Amazon with a data structure, which defines their purpose and
   * what type of resources (elb or ec2) are able to make use of them. We also need to ensure that the Subnet IDs that
   * we provide back are able to be deployed to based off of the supplied availability zones.
   *
   * @return list of subnet ids applicable to this deployment.
   */
  List<String> getSubnetIds(List<Subnet> allSubnetsForTypeAndAvailabilityZone) {
    def subnetIds = allSubnetsForTypeAndAvailabilityZone*.subnetId

    def invalidSubnetIds = (this.subnetIds ?: []).findAll { !subnetIds.contains(it) }
    if (invalidSubnetIds) {
      throw new IllegalStateException(
        "One or more subnet ids are not valid (invalidSubnetIds: ${invalidSubnetIds.join(", ")}, availabilityZones: ${availabilityZones})"
      )
    }

    return this.subnetIds ?: subnetIds
  }

  private List<Subnet> getSubnets(boolean filterForSubnetPurposeTags = true) {
    if (!subnetType) {
      return []
    }

    DescribeSubnetsResult result = regionScopedProvider.amazonEC2.describeSubnets()
    List<Subnet> mySubnets = []
    for (subnet in result.subnets) {
      if (availabilityZones && !availabilityZones.contains(subnet.availabilityZone)) {
        continue
      }
      if (filterForSubnetPurposeTags) {
        SubnetData sd = SubnetData.from(subnet)
        if (sd.purpose == subnetType && (sd.target == null || sd.target == SubnetTarget.EC2)) {
          mySubnets << subnet
        }
      } else {
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
      .withLoadBalancerNames(classicLoadBalancers)
      .withTargetGroupARNs(targetGroupArns)
      .withDefaultCooldown(cooldown)
      .withHealthCheckGracePeriod(healthCheckGracePeriod)
      .withHealthCheckType(healthCheckType)
      .withTerminationPolicies(terminationPolicies)

    tags?.each { key, value ->
      request.withTags(new Tag()
                        .withKey(key)
                        .withValue(value)
                        .withPropagateAtLaunch(true))
    }

    // if we have explicitly specified subnetIds, don't require that they are tagged with a subnetType/purpose
    boolean filterForSubnetPurposeTags = !this.subnetIds
    // Favor subnetIds over availability zones
    def subnetIds = getSubnetIds(getSubnets(filterForSubnetPurposeTags))?.join(',')
    if (subnetIds) {
      task.updateStatus AWS_PHASE, " > Deploying to subnetIds: $subnetIds"
      request.withVPCZoneIdentifier(subnetIds)
    } else if (subnetType && !getSubnets()) {
      throw new RuntimeException("No suitable subnet was found for internal subnet purpose '${subnetType}'!")
    } else {
      task.updateStatus AWS_PHASE, "Deploying to availabilityZones: $availabilityZones"
      request.withAvailabilityZones(availabilityZones)
    }

    def autoScaling = regionScopedProvider.autoScaling
    Exception ex = retrySupport.retry({ ->
      try {
        autoScaling.createAutoScalingGroup(request)
        return null
      } catch (AlreadyExistsException e) {
        if (!shouldProceedWithExistingState(autoScaling, asgName, request)) {
          return e
        }
        log.debug("Determined pre-existing ASG is desired state, continuing...", e)
        return null
      }
    }, 10, 1000, false)
    if (ex != null) {
      throw ex
    }

    if (suspendedProcesses) {
      retrySupport.retry({ ->
        autoScaling.suspendProcesses(new SuspendProcessesRequest(autoScalingGroupName: asgName, scalingProcesses: suspendedProcesses))
      }, 10, 1000, false)
    }
    if (enabledMetrics && instanceMonitoring) {
      task.updateStatus AWS_PHASE, "Enabling metrics collection for: $asgName"
      retrySupport.retry({ ->
        autoScaling.enableMetricsCollection(new EnableMetricsCollectionRequest()
          .withAutoScalingGroupName(asgName)
          .withGranularity('1Minute')
          .withMetrics(enabledMetrics))
      }, 10, 1000, false)
    }

    retrySupport.retry({ ->
      task.updateStatus AWS_PHASE, "Setting size of $asgName in ${credentials.name}/$region to " +
        "[min=$minInstances, max=$maxInstances, desired=$desiredInstances]"
      autoScaling.updateAutoScalingGroup(
        new UpdateAutoScalingGroupRequest(
          autoScalingGroupName: asgName,
          minSize: minInstances,
          maxSize: maxInstances,
          desiredCapacity: desiredInstances
        )
      )
    }, 10, 1000, false)

    asgName
  }

  private boolean shouldProceedWithExistingState(AmazonAutoScaling autoScaling, String asgName, CreateAutoScalingGroupRequest request) {
    DescribeAutoScalingGroupsResult result = autoScaling.describeAutoScalingGroups(
      new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName)
    )
    if (result.autoScalingGroups.isEmpty()) {
      // This will only happen if we get an AlreadyExistsException from AWS, then immediately after describing it, we
      // don't get a result back. We'll continue with trying to create because who knows... may as well try.
      log.error("Attempted to find pre-existing ASG but none was found: $asgName")
      return true
    }
    AutoScalingGroup existingAsg = result.autoScalingGroups.first()

    Set<String> failedPredicates = [
      "launch configuration": { return existingAsg.launchConfigurationName == request.launchConfigurationName },
      "availability zones": { return existingAsg.availabilityZones.sort() == request.availabilityZones.sort() },
      "subnets": { return existingAsg.getVPCZoneIdentifier()?.split(",")?.sort()?.toList() == request.getVPCZoneIdentifier()?.split(",")?.sort()?.toList() },
      "load balancers": { return existingAsg.loadBalancerNames.sort() == request.loadBalancerNames.sort() },
      "target groups": { return existingAsg.targetGroupARNs.sort() == request.targetGroupARNs.sort() },
      "cooldown": { return existingAsg.defaultCooldown == request.defaultCooldown },
      "health check grace period": { return existingAsg.healthCheckGracePeriod == request.healthCheckGracePeriod },
      "health check type": { return existingAsg.healthCheckType == request.healthCheckType },
      "termination policies": { return existingAsg.terminationPolicies.sort() == request.terminationPolicies.sort() }
    ].findAll { !((Supplier<Boolean>) it.value).get() }.keySet()

    if (!failedPredicates.isEmpty()) {
      task.updateStatus AWS_PHASE, "$asgName already exists and does not seem to match desired state on: ${failedPredicates.join(", ")}"
      return false
    }
    if (existingAsg.createdTime.toInstant().isBefore(Instant.now().minus(1, ChronoUnit.HOURS))) {
      task.updateStatus AWS_PHASE, "$asgName already exists and appears to be valid, but falls outside of safety window for idempotent deploy (1 hour)"
      return false
    }

    return true
  }
}
