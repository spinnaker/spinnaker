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

package com.netflix.spinnaker.clouddriver.aws.deploy.asg;

import com.netflix.spinnaker.clouddriver.aws.deploy.asg.asgbuilders.AsgBuilder;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice;
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * A worker class dedicated to the deployment of "applications", following many of Netflix's common
 * AWS conventions.
 */
@Slf4j
public class AutoScalingWorker {
  private static final String AWS_PHASE = "AWS_DEPLOY";
  private RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider;
  private DynamicConfigService dynamicConfigService;

  AutoScalingWorker(
      RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider,
      DynamicConfigService dynamicConfigService) {
    this.regionScopedProvider = regionScopedProvider;
    this.dynamicConfigService = dynamicConfigService;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  /**
   * Initiates the activity of deployment. This will involve:
   *
   * <ol>
   *   <li>Lookup or create if not found, a security group with a name that matches the supplied
   *       "application";
   *   <li>Looking up security group ids for the names provided as "securityGroups";
   *   <li>Look up an ancestor ASG based on Netflix naming conventions, and bring its security
   *       groups to the new ASG;
   *   <li>Retrieve user data from all available {@link
   *       com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProvider}s;
   *   <li>Create the ASG's Launch Configuration or Launch Template with User Data and Security
   *       Groups;
   *   <li>Create a new ASG in the subnets found from the optionally supplied subnetType.
   * </ol>
   *
   * @return the name of the newly deployed ASG
   */
  public String deploy(AsgConfiguration asgConfig) {
    getTask().updateStatus(AWS_PHASE, "Beginning Amazon deployment.");

    if (asgConfig.startDisabled != null && asgConfig.startDisabled) {
      asgConfig.suspendedProcesses.addAll(
          AutoScalingProcessType.getDisableProcesses().stream()
              .map(AutoScalingProcessType::name)
              .collect(Collectors.toList()));
    }

    getTask().updateStatus(AWS_PHASE, "Beginning ASG deployment.");

    AWSServerGroupNameResolver awsServerGroupNameResolver =
        regionScopedProvider.getAWSServerGroupNameResolver();
    String asgName;
    if (asgConfig.sequence != null) {
      asgName =
          awsServerGroupNameResolver.generateServerGroupName(
              asgConfig.application,
              asgConfig.stack,
              asgConfig.freeFormDetails,
              asgConfig.sequence,
              false);
    } else {
      asgName =
          awsServerGroupNameResolver.resolveNextServerGroupName(
              asgConfig.application,
              asgConfig.stack,
              asgConfig.freeFormDetails,
              asgConfig.ignoreSequence);
    }

    AsgBuilder asgBuilder;
    if (shouldSetLaunchTemplate(asgConfig)) {
      // process the IPv6 setting conditionally
      if (asgConfig.getAssociateIPv6Address() == null) {
        String asgConfigEnv = asgConfig.getCredentials().getEnvironment();
        Boolean autoEnableIPv6 =
            dynamicConfigService.getConfig(
                Boolean.class, "aws.features.launch-templates.ipv6." + asgConfigEnv, false);
        asgConfig.setAssociateIPv6Address(autoEnableIPv6);
      }

      if (asgConfig.shouldUseMixedInstancesPolicy()) {
        asgBuilder = regionScopedProvider.getAsgBuilderForMixedInstancesPolicy();
      } else {
        asgBuilder = regionScopedProvider.getAsgBuilderForLaunchTemplate();
      }
    } else {
      asgBuilder = regionScopedProvider.getAsgBuilderForLaunchConfiguration();
    }

    return asgBuilder.build(getTask(), AWS_PHASE, asgName, asgConfig);
  }
  /** This is used to gradually roll out launch template. */
  private boolean shouldSetLaunchTemplate(final AsgConfiguration asgConfig) {
    // Request level flag that forces launch configurations.
    if (asgConfig.setLaunchTemplate == null || !asgConfig.setLaunchTemplate) {
      return false;
    }

    // Property flag to turn off launch template feature. Caching agent might require bouncing the
    // java process
    if (!dynamicConfigService.isEnabled("aws.features.launch-templates", false)) {
      log.debug("Launch Template feature disabled via configuration.");
      return false;
    }

    // This is a comma separated list of applications to exclude
    String excludedApps =
        dynamicConfigService.getConfig(
            String.class, "aws.features.launch-templates.excluded-applications", "");
    for (String excludedApp : excludedApps.split(",")) {
      if (excludedApp.trim().equals(asgConfig.getApplication())) {
        return false;
      }
    }

    // This is a comma separated list of accounts to exclude
    String excludedAccounts =
        dynamicConfigService.getConfig(
            String.class, "aws.features.launch-templates.excluded-accounts", "");
    for (String excludedAccount : excludedAccounts.split(",")) {
      if (excludedAccount.trim().equals(asgConfig.getCredentials().getName())) {
        return false;
      }
    }

    // Allows everything that is not excluded
    if (dynamicConfigService.isEnabled("aws.features.launch-templates.all-applications", false)) {
      return true;
    }

    // Application allow list with the following format:
    // app1:account:region1,app2:account:region1
    // This allows more control over what account and region pairs to enable for this deployment.
    String allowedApps =
        dynamicConfigService.getConfig(
            String.class, "aws.features.launch-templates.allowed-applications", "");
    if (matchesAppAccountAndRegion(
        asgConfig.getApplication(),
        asgConfig.getCredentials().getName(),
        asgConfig.getRegion(),
        allowedApps.split(","))) {
      return true;
    }

    // An allow list for account/region pairs with the following format:
    // account:region
    String allowedAccountsAndRegions =
        dynamicConfigService.getConfig(
            String.class, "aws.features.launch-templates.allowed-accounts-regions", "");
    for (String accountRegion : allowedAccountsAndRegions.split(",")) {
      if (StringUtils.isNotBlank(accountRegion) && accountRegion.contains(":")) {
        String[] parts = accountRegion.split(":");
        String account = parts[0];
        String region = parts[1];
        if (account.trim().equals(asgConfig.getCredentials().getName())
            && region.trim().equals(asgConfig.getRegion())) {
          return true;
        }
      }
    }
    // This is a comma separated list of accounts to allow
    String allowedAccounts =
        dynamicConfigService.getConfig(
            String.class, "aws.features.launch-templates.allowed-accounts", "");
    for (String allowedAccount : allowedAccounts.split(",")) {
      if (allowedAccount.trim().equals(asgConfig.getCredentials().getName())) {
        return true;
      }
    }

    return false;
  }

  /**
   * Helper function to parse and match an array of app:account:region1,...,app:account,region to
   * the specified application, account and region. Used to flag launch template feature and rollout
   */
  static boolean matchesAppAccountAndRegion(
      String application, String accountName, String region, String... applicationAccountRegions) {
    if (applicationAccountRegions != null && applicationAccountRegions.length <= 0) {
      return false;
    }

    for (String appAccountRegion : applicationAccountRegions) {
      if (StringUtils.isNotBlank(appAccountRegion) && appAccountRegion.contains(":")) {
        try {
          String[] parts = appAccountRegion.split(":");
          String app = parts[0];
          String account = parts[1];
          String regions = parts[2];
          if (app.equals(application)
              && account.equals(accountName)
              && Arrays.asList(regions.split(",")).contains(region)) {
            return true;
          }
        } catch (Exception e) {
          log.error(
              "Unable to verify if application is allowed in shouldSetLaunchTemplate: {}",
              appAccountRegion);
          return false;
        }
      }
    }

    return false;
  }

  @Data
  @Builder
  @AllArgsConstructor
  public static class AsgConfiguration {
    private String application;
    private String region;
    private NetflixAmazonCredentials credentials;
    private String stack;
    private String freeFormDetails;
    private String ami;
    private String classicLinkVpcId;
    private List<String> classicLinkVpcSecurityGroups;
    private String instanceType;
    private String iamRole;
    private String keyPair;
    private String base64UserData;
    private Boolean legacyUdf;
    private UserDataOverride userDataOverride;
    private Integer sequence;
    private Boolean ignoreSequence;
    private Boolean startDisabled;
    private Boolean associatePublicIpAddress;
    private String subnetType;
    private List<String> subnetIds;
    private Integer cooldown;
    private Collection<String> enabledMetrics;
    private Integer healthCheckGracePeriod;
    private String healthCheckType;
    private String spotMaxPrice;
    private Set<String> suspendedProcesses;
    private Collection<String> terminationPolicies;
    private String kernelId;
    private String ramdiskId;
    private Boolean instanceMonitoring;
    private Boolean ebsOptimized;
    private Collection<String> classicLoadBalancers;
    private Collection<String> targetGroupArns;
    private List<String> securityGroups;
    private List<String> availabilityZones;
    private List<AmazonBlockDevice> blockDevices;
    private Map<String, String> tags;
    private Map<String, String> blockDeviceTags;
    private List<AmazonAsgLifecycleHook> lifecycleHooks;
    private Boolean capacityRebalance;
    private int minInstances;
    private int maxInstances;
    private int desiredInstances;

    /** Launch Templates properties * */
    private Boolean setLaunchTemplate;

    private Boolean requireIMDSv2;
    private Boolean associateIPv6Address;
    private Boolean unlimitedCpuCredits;
    private BasicAmazonDeployDescription.LaunchTemplatePlacement placement;
    private List<BasicAmazonDeployDescription.LaunchTemplateLicenseSpecification>
        licenseSpecifications;
    private Boolean enableEnclave;

    /** Mixed Instances Policy properties * */
    private String onDemandAllocationStrategy;

    private Integer onDemandBaseCapacity;
    private Integer onDemandPercentageAboveBaseCapacity;
    private String spotAllocationStrategy;
    private Integer spotInstancePools;
    private List<BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType>
        launchTemplateOverridesForInstanceType;

    /**
     * AsgConfiguration object makes sense only when created with all or some of the configuration
     * fields.
     */
    private AsgConfiguration() {}

    /**
     * Helper function to determine if the ASG should be created with mixed instances policy, when
     * launch templates are enabled
     *
     * @return boolean true if mixed instances policy parameters are used, false otherwise
     */
    public boolean shouldUseMixedInstancesPolicy() {
      for (String fieldName : BasicAmazonDeployDescription.getMixedInstancesPolicyFieldNames()) {
        try {
          if (this.getClass().getDeclaredField(fieldName).get(this) != null) {
            return true;
          }
        } catch (NoSuchFieldException | IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
      return false;
    }
  }
}
