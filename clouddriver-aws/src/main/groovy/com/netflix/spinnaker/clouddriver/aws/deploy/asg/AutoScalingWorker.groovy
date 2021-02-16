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

package com.netflix.spinnaker.clouddriver.aws.deploy.asg


import com.netflix.spinnaker.clouddriver.aws.deploy.asg.asgbuilders.*
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import groovy.util.logging.Slf4j

/**
 * A worker class dedicated to the deployment of "applications", following many of Netflix's common AWS conventions.
 */
@Slf4j
class AutoScalingWorker {
  private static final String AWS_PHASE = "AWS_DEPLOY"
  private RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider
  private DynamicConfigService dynamicConfigService

  AutoScalingWorker(RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider, DynamicConfigService dynamicConfigService) {
    this.regionScopedProvider = regionScopedProvider
    this.dynamicConfigService = dynamicConfigService
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

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

    AsgBuilder asgBuilder
    if (shouldSetLaunchTemplate(asgConfig)) {

      // process the IPv6 setting conditionally
      if(asgConfig.getAssociateIPv6Address() == null) {
        def asgConfigEnv = asgConfig.getCredentials().getEnvironment()
        def autoEnableIPv6 = dynamicConfigService.getConfig(Boolean.class, "aws.features.launch-templates.ipv6.${asgConfigEnv}", false)
        asgConfig.setAssociateIPv6Address(autoEnableIPv6)
      }

      // get ASG builder for launch template
      asgBuilder = regionScopedProvider.getAsgBuilderForLaunchTemplate()
    } else {
      // get ASG builder for launch configuration
      asgBuilder = regionScopedProvider.getAsgBuilderForLaunchConfiguration()
    }

    asgBuilder.build(task, AWS_PHASE, asgName, asgConfig)
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
