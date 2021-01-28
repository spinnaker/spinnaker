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

package com.netflix.spinnaker.clouddriver.aws.deploy

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AlreadyExistsException
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.EnableMetricsCollectionRequest
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification
import com.amazonaws.services.autoscaling.model.SuspendProcessesRequest
import com.amazonaws.services.autoscaling.model.Tag
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.amazonaws.services.ec2.model.DescribeSubnetsResult
import com.amazonaws.services.ec2.model.LaunchTemplate
import com.amazonaws.services.ec2.model.Subnet
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.clouddriver.aws.model.SubnetData
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import groovy.transform.AutoClone
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

  private final RetrySupport retrySupport = new RetrySupport()

  private RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider
  private DynamicConfigService dynamicConfigService

  AutoScalingWorker(RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider, DynamicConfigService dynamicConfigService) {
    this.regionScopedProvider = regionScopedProvider
    this.dynamicConfigService = dynamicConfigService
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @AutoClone
  static class AsgConfiguration {
    String application
    String region
    NetflixAmazonCredentials credentials
    String stack
    String freeFormDetails
    String ami
    String classicLinkVpcId
    List<String> classicLinkVpcSecurityGroups
    String instanceType
    String iamRole
    String keyPair
    String base64UserData
    Boolean legacyUdf
    UserDataOverride userDataOverride
    Integer sequence
    Boolean ignoreSequence
    Boolean startDisabled
    Boolean associatePublicIpAddress
    String subnetType
    List<String> subnetIds
    Integer cooldown
    Collection<String> enabledMetrics
    Integer healthCheckGracePeriod
    String healthCheckType
    String spotMaxPrice
    Set<String> suspendedProcesses
    Collection<String> terminationPolicies
    String kernelId
    String ramdiskId
    Boolean instanceMonitoring
    Boolean ebsOptimized
    Collection<String> classicLoadBalancers
    Collection<String> targetGroupArns
    List<String> securityGroups
    List<String> availabilityZones
    List<AmazonBlockDevice> blockDevices
    Map<String, String> tags
    List<AmazonAsgLifecycleHook> lifecycleHooks
    int minInstances
    int maxInstances
    int desiredInstances

    /** Launch Templates properties **/
    Boolean setLaunchTemplate
    Boolean requireIMDSv2
    Boolean associateIPv6Address
    Boolean unlimitedCpuCredits
    BasicAmazonDeployDescription.LaunchTemplatePlacement placement
    List<BasicAmazonDeployDescription.LaunchTemplateLicenseSpecification> licenseSpecifications
  }

  /**
   * Initiates the activity of deployment. This will involve:
   *  <ol>
   *    <li>Lookup or create if not found, a security group with a name that matches the supplied "application";</li>
   *    <li>Looking up security group ids for the names provided as "securityGroups";</li>
   *    <li>Look up an ancestor ASG based on Netflix naming conventions, and bring its security groups to the new ASG;</li>
   *    <li>Retrieve user data from all available {@link com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProvider}s;</li>
   *    <li>Create the ASG's Launch Configuration or Launch Template with User Data and Security Groups;</li>
   *    <li>Create a new ASG in the subnets found from the optionally supplied subnetType.</li>
   *  </ol>
   *
   * @return the name of the newly deployed ASG
   */
  String deploy(AsgConfiguration asgConfig) {
    task.updateStatus AWS_PHASE, "Beginning Amazon deployment."

    if (asgConfig.startDisabled) {
      asgConfig.suspendedProcesses.addAll(AutoScalingProcessType.getDisableProcesses()*.name())
    }

    task.updateStatus AWS_PHASE, "Beginning ASG deployment."

    AWSServerGroupNameResolver awsServerGroupNameResolver = regionScopedProvider.AWSServerGroupNameResolver
    String asgName
    if (asgConfig.sequence != null) {
      asgName = awsServerGroupNameResolver.generateServerGroupName(
              asgConfig.application,
              asgConfig.stack,
              asgConfig.freeFormDetails,
              asgConfig.sequence,
              false)
    } else {
      asgName = awsServerGroupNameResolver.resolveNextServerGroupName(
              asgConfig.application,
              asgConfig.stack,
              asgConfig.freeFormDetails,
              asgConfig.ignoreSequence)
    }

    LaunchTemplateSpecification launchTemplateSpecification = null
    String launchConfigName = null

    if (shouldSetLaunchTemplate(asgConfig)) {
      def asgConfigWithSecGroups = AsgConfigHelper.setAppSecurityGroups(
              asgConfig,
              regionScopedProvider.getSecurityGroupService(),
              regionScopedProvider.getDeploymentDefaults())
      def launchTemplateName = AsgConfigHelper.createName(asgName, null)

      final LaunchTemplate launchTemplate = regionScopedProvider
              .getLaunchTemplateService()
              .createLaunchTemplate(asgConfigWithSecGroups, asgName, launchTemplateName)
      launchTemplateSpecification = new LaunchTemplateSpecification(
              launchTemplateId: launchTemplate.launchTemplateId,
              version: launchTemplate.latestVersionNumber)
    } else {
      def settings = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
              account: asgConfig.credentials.name,
              environment: asgConfig.credentials.environment,
              accountType: asgConfig.credentials.accountType,
              region: asgConfig.region,
              baseName: asgName,
              suffix: null,
              ami: asgConfig.ami,
              iamRole: asgConfig.iamRole,
              classicLinkVpcId: asgConfig.classicLinkVpcId,
              classicLinkVpcSecurityGroups: asgConfig.classicLinkVpcSecurityGroups,
              instanceType: asgConfig.instanceType,
              keyPair: asgConfig.keyPair,
              base64UserData: asgConfig.base64UserData,
              associatePublicIpAddress: asgConfig.associatePublicIpAddress,
              kernelId: asgConfig.kernelId,
              ramdiskId: asgConfig.ramdiskId,
              ebsOptimized: asgConfig.ebsOptimized,
              spotPrice: asgConfig.spotMaxPrice,
              instanceMonitoring: asgConfig.instanceMonitoring,
              blockDevices: asgConfig.blockDevices,
              securityGroups: asgConfig.securityGroups)

      launchConfigName = regionScopedProvider.getLaunchConfigurationBuilder().buildLaunchConfiguration(
              asgConfig.application,
              asgConfig.subnetType,
              settings,
              asgConfig.legacyUdf,
              asgConfig.userDataOverride)
    }

    task.updateStatus AWS_PHASE, "Deploying ASG: $asgName"
    createAutoScalingGroup(asgConfig, asgName, launchConfigName, launchTemplateSpecification)
  }

  /**
   * This is an obscure rule that Subnets are tagged at Amazon with a data structure, which defines their purpose and
   * what type of resources (elb or ec2) are able to make use of them. We also need to ensure that the Subnet IDs that
   * we provide back are able to be deployed to based off of the supplied availability zones.
   *
   * @return list of subnet ids applicable to this deployment.
   */
  List<String> getSubnetIds(List<Subnet> allSubnetsForTypeAndAvailabilityZone, List<String> subnetIds, List<String> availabilityZones) {
    def allSubnetIds = allSubnetsForTypeAndAvailabilityZone*.subnetId

    def invalidSubnetIds = (subnetIds ?: []).findAll { !allSubnetIds.contains(it) }
    if (invalidSubnetIds) {
      throw new IllegalStateException(
        "One or more subnet ids are not valid (invalidSubnetIds: ${invalidSubnetIds.join(", ")}, availabilityZones: ${availabilityZones.join(", ")})"
      )
    }

    return subnetIds ?: allSubnetIds
  }

  private List<Subnet> getSubnets(boolean filterForSubnetPurposeTags = true, String subnetType, List<String> availabilityZones) {
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
   * @param launchTemplateSpecification when defined, the server group is created with the provided launch template specification
   * @return
   */
  String createAutoScalingGroup(AsgConfiguration asgConfig, String asgName, String launchConfigurationName, LaunchTemplateSpecification launchTemplateSpecification = null) {
    CreateAutoScalingGroupRequest request = new CreateAutoScalingGroupRequest()
      .withAutoScalingGroupName(asgName)
      .withMinSize(0)
      .withMaxSize(0)
      .withDesiredCapacity(0)
      .withLoadBalancerNames(asgConfig.classicLoadBalancers)
      .withTargetGroupARNs(asgConfig.targetGroupArns)
      .withDefaultCooldown(asgConfig.cooldown)
      .withHealthCheckGracePeriod(asgConfig.healthCheckGracePeriod)
      .withHealthCheckType(asgConfig.healthCheckType)
      .withTerminationPolicies(asgConfig.terminationPolicies)

    if (launchTemplateSpecification != null) {
      request.withLaunchTemplate(launchTemplateSpecification)
    } else {
      request.withLaunchConfigurationName(launchConfigurationName)
    }

    asgConfig.tags?.each { key, value ->
      request.withTags(new Tag()
                        .withKey(key)
                        .withValue(value)
                        .withPropagateAtLaunch(true))
    }

    // if we have explicitly specified subnetIds, don't require that they are tagged with a subnetType/purpose
    boolean filterForSubnetPurposeTags = !asgConfig.subnetIds
    // Favor subnetIds over availability zones
    def subnetIds = getSubnetIds(getSubnets(
                                    filterForSubnetPurposeTags,
                                    asgConfig.subnetType,
                                    asgConfig.availabilityZones),
                                asgConfig.subnetIds,
                                asgConfig.availabilityZones)?.join(',')
    if (subnetIds) {
      task.updateStatus AWS_PHASE, " > Deploying to subnetIds: $subnetIds"
      request.withVPCZoneIdentifier(subnetIds)
    } else if (asgConfig.subnetType && !getSubnets(subnetType: asgConfig.subnetType, availabilityZones: asgConfig.availabilityZones)) {
      throw new RuntimeException("No suitable subnet was found for internal subnet purpose '${asgConfig.subnetType}'!")
    } else {
      task.updateStatus AWS_PHASE, "Deploying to availabilityZones: $asgConfig.availabilityZones"
      request.withAvailabilityZones(asgConfig.availabilityZones)
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

    if (asgConfig.lifecycleHooks != null && !asgConfig.lifecycleHooks.isEmpty()) {
      Exception e = retrySupport.retry({ ->
        task.updateStatus AWS_PHASE, "Creating lifecycle hooks for: $asgName"
        regionScopedProvider.asgLifecycleHookWorker.attach(task, asgConfig.lifecycleHooks, asgName)
      }, 10, 1000, false)

      if (e != null) {
        task.updateStatus AWS_PHASE, "Unable to attach lifecycle hooks to ASG ($asgName): ${e.message}"
      }
    }

    if (asgConfig.suspendedProcesses) {
      retrySupport.retry({ ->
        autoScaling.suspendProcesses(new SuspendProcessesRequest(autoScalingGroupName: asgName, scalingProcesses: asgConfig.suspendedProcesses))
      }, 10, 1000, false)
    }

    if (asgConfig.enabledMetrics && asgConfig.instanceMonitoring) {
      task.updateStatus AWS_PHASE, "Enabling metrics collection for: $asgName"
      retrySupport.retry({ ->
        autoScaling.enableMetricsCollection(new EnableMetricsCollectionRequest()
          .withAutoScalingGroupName(asgName)
          .withGranularity('1Minute')
          .withMetrics(asgConfig.enabledMetrics))
      }, 10, 1000, false)
    }

    retrySupport.retry({ ->
      task.updateStatus AWS_PHASE, "Setting size of $asgName in ${asgConfig.credentials.name}/$asgConfig.region to " +
        "[min=$asgConfig.minInstances, max=$asgConfig.maxInstances, desired=$asgConfig.desiredInstances]"
      autoScaling.updateAutoScalingGroup(
        new UpdateAutoScalingGroupRequest(
          autoScalingGroupName: asgName,
          minSize: asgConfig.minInstances,
          maxSize: asgConfig.maxInstances,
          desiredCapacity: asgConfig.desiredInstances
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
      "launch template": { return existingAsg.launchTemplate == request.launchTemplate },
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

  /**
   * This is used to gradually roll out launch template.
   */
  private boolean shouldSetLaunchTemplate(final AsgConfiguration asgConfig) {
    // Request level flag that forces launch configurations.
    if (!asgConfig.setLaunchTemplate) {
      return false
    }

    // Property flag to turn off launch template feature. Caching agent might require bouncing the java process
    if (!dynamicConfigService.isEnabled("aws.features.launch-templates", false)) {
      log.debug("Launch Template feature disabled via configuration.")
      return false
    }

    // This is a comma separated list of applications to exclude
    String excludedApps = dynamicConfigService
      .getConfig(String.class, "aws.features.launch-templates.excluded-applications", "")
    for (excludedApp in excludedApps.split(",")) {
      if (excludedApp.trim() == asgConfig.application) {
        return false
      }
    }

    // This is a comma separated list of accounts to exclude
    String excludedAccounts = dynamicConfigService.getConfig(String.class, "aws.features.launch-templates.excluded-accounts", "")
    for (excludedAccount in excludedAccounts.split(",")) {
      if (excludedAccount.trim() == asgConfig.credentials.name) {
        return false
      }
    }

    // Allows everything that is not excluded
    if (dynamicConfigService.isEnabled("aws.features.launch-templates.all-applications", false)) {
      return true
    }

    // Application allow list with the following format:
    // app1:account:region1,app2:account:region1
    // This allows more control over what account and region pairs to enable for this deployment.
    String allowedApps = dynamicConfigService
      .getConfig(String.class, "aws.features.launch-templates.allowed-applications", "")
    if (matchesAppAccountAndRegion(asgConfig.application, asgConfig.credentials.name, asgConfig.region, allowedApps.split(","))) {
      return true
    }

    // An allow list for account/region pairs with the following format:
    // account:region
    String allowedAccountsAndRegions = dynamicConfigService
      .getConfig(String.class, "aws.features.launch-templates.allowed-accounts-regions", "")
    for (accountRegion in allowedAccountsAndRegions.split(",")) {
      if (accountRegion && accountRegion.contains(":")) {
        def (account, region) = accountRegion.split(":")
        if (account.trim() == asgConfig.credentials.name && region.trim() == asgConfig.region) {
          return true
        }
      }
    }

    // This is a comma separated list of accounts to allow
    String allowedAccounts = dynamicConfigService.getConfig(String.class, "aws.features.launch-templates.allowed-accounts", "")
    for (allowedAccount in allowedAccounts.split(",")) {
      if (allowedAccount.trim() == asgConfig.credentials.name) {
        return true
      }
    }

    return false
  }

  /**
   * Helper function to parse and match an array of app:account:region1,...,app:account,region
   * to the specified application, account and region
   * Used to flag launch template feature and rollout
   */
  static boolean matchesAppAccountAndRegion(
    String application, String accountName, String region, String... applicationAccountRegions) {
    if (!applicationAccountRegions) {
      return false
    }

    for (appAccountRegion in applicationAccountRegions) {
      if (appAccountRegion && appAccountRegion.contains(":")) {
        try {
          def (app, account, regions) = appAccountRegion.split(":")
          if (app == application && account == accountName && region in (regions as String).split(",")) {
            return true
          }
        } catch (Exception e) {
          log.error("Unable to verify if application is allowed in shouldSetLaunchTemplate: ${appAccountRegion}")
          return false
        }


      }
    }

    return false
  }
}
