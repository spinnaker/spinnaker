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

package com.netflix.spinnaker.clouddriver.aws.deploy.description

import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class BasicAmazonDeployDescription extends AbstractAmazonCredentialsDescription implements
  DeployDescription, ApplicationNameable {
  String application
  String amiName
  String stack
  String freeFormDetails
  String instanceType
  String subnetType
  List<String> subnetIds
  String iamRole
  String keyPair
  Boolean associatePublicIpAddress
  Integer cooldown
  Collection<String> enabledMetrics
  Integer healthCheckGracePeriod
  String healthCheckType
  Set<String> suspendedProcesses = []
  Collection<String> terminationPolicies
  String kernelId
  String ramdiskId
  Boolean instanceMonitoring
  Boolean ebsOptimized
  String base64UserData
  Boolean legacyUdf
  UserDataOverride userDataOverride = new UserDataOverride()

  Collection<OperationEvent> events = []

  //Default behaviour (legacy reasons) is to carry forward some settings (even on a deploy vs a cloneServerGroup) from an ancestor ASG
  // the following flags disable copying of those attributes:

  /**
   * If false, the newly created server group will not pick up scaling policies and actions from an ancestor group
   */
  boolean copySourceScalingPoliciesAndActions = true

  /**
   * If false, the newly created server group will not pick up block device mapping customizations from an ancestor group
   */
  boolean copySourceCustomBlockDeviceMappings = true

  /**
   * If false, the newly created server group will not pick up overridden subnet ids from an ancestor group
   */
  boolean copySourceSubnetIdOverrides = true

  String classicLinkVpcId
  List<String> classicLinkVpcSecurityGroups

  /**
   * If specified, this sequence number will be used when generating the server group name.
   *
   * Expectation is on the caller to ensure that an explicitly provided sequence number is not already in use.
   */
  Integer sequence

  boolean ignoreSequence
  boolean startDisabled
  boolean includeAccountLifecycleHooks = true

  List<AmazonBlockDevice> blockDevices
  Boolean useAmiBlockDeviceMappings
  List<String> loadBalancers
  List<String> targetGroups
  List<String> securityGroups
  List<String> securityGroupNames = []
  List<AmazonAsgLifecycleHook> lifecycleHooks = []
  Map<String, List<String>> availabilityZones = [:]
  Capacity capacity = new Capacity()
  Source source = new Source()
  Map<String, String> tags
  Map<String, String> blockDeviceTags

  // Launch Template features:start
  /**
   * When set to true, the created server group will use a launch template instead of a launch configuration.
   */
  Boolean setLaunchTemplate = true

  /**
   * When set to true, the created server group will be configured with IMDSv2.
   * This is a Launch Template only feature
   * * https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-instance-metadata-service.html
   */
  Boolean requireIMDSv2 = false

  /**
   * Associate an IPv6 address
   * This is a Launch Template only feature
   */
  Boolean associateIPv6Address

  /**
   * Applicable only for burstable performance instance types like t2/t3.
   * * set to null when not applicable / by default.
   *
   * The burstable performance instances in the created server group will have:
   * * unlimited CPU credits, when set to true
   * * standard CPU credits, when set to false
   * https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-performance-instances-unlimited-mode.html
   *
   * This is a Launch Template only feature.
   */
  Boolean unlimitedCpuCredits

  /**
   * When set to true, the created server group will be configured with Nitro Enclaves enabled
   * This is a Launch Template only feature
   * * https://docs.aws.amazon.com/enclaves/latest/user/nitro-enclave.html
   */
  Boolean enableEnclave

  /**
   * Launch template placement details, see {@link com.amazonaws.services.ec2.model.LaunchTemplatePlacementRequest}.
   */
  LaunchTemplatePlacement placement

  /**
   * Launch template license specifications, see {@link com.amazonaws.services.ec2.model.LaunchTemplateLicenseConfigurationRequest}.
   */
  List<LaunchTemplateLicenseSpecification> licenseSpecifications

  /**
   * Indicates how to allocate instance types to fulfill On-Demand capacity. The only valid value is prioritized.
   * This strategy uses the order of instance types in the LaunchTemplateOverrides to define the launch priority of each instance type.
   * default: prioritized
   */
  String onDemandAllocationStrategy

  /** The minimum amount of the Auto Scaling group's capacity that must be fulfilled by On-Demand Instances.
   * If weights are specified for the instance types in the overrides,
   * set the value of OnDemandBaseCapacity in terms of the number of capacity units, and not number of instances.
   * default: 0
   */
  Integer onDemandBaseCapacity

  /**
   * The percentages of On-Demand Instances and Spot Instances for additional capacity beyond OnDemandBaseCapacity
   * default: 100, i.e. only On-Demand instances
   */
  Integer onDemandPercentageAboveBaseCapacity

  /**
   * Indicates how to allocate instances across Spot Instance pools. 2 strategies:
   * 1) capacity-optimized (recommended): instances launched using Spot pools that are optimally chosen based on the available Spot capacity.
   * 2) lowest-price: instances launched using Spot pools with the lowest price, and evenly allocated across the number of Spot pools specified in spotInstancePools.
   * default: lowest-price
   */
  String spotAllocationStrategy

  /**
   * The number of Spot Instance pools across which to allocate Spot Instances. The Spot pools are determined from the different instance types in the overrides.
   * default: 2, only applicable with 'lowest-price' spotAllocationStrategy
   * limits: 1 to 20
   */
  Integer spotInstancePools

  /**
   * The maximum price per unit hour that the user is willing to pay for a Spot Instance.
   * default: On-Demand price for the configuration
   */
  String spotPrice

  /**
   * A list of parameters to override corresponding parameters in the launch template.
   * limits:
   * * instances that can be associated with an ASG: 40
   * * distinct launch templates that can be associated with an ASG: 20
   */
  List<LaunchTemplateOverridesForInstanceType> launchTemplateOverridesForInstanceType

  static Set<String> getLaunchTemplateOnlyFieldNames() {
    return ["requireIMDSv2", "associateIPv6Address", "unlimitedCpuCredits",
            "placement", "licenseSpecifications", "onDemandAllocationStrategy",
            "onDemandBaseCapacity", "onDemandPercentageAboveBaseCapacity", "spotAllocationStrategy",
            "spotInstancePools", "launchTemplateOverridesForInstanceType", "enableEnclave"].toSet()
  }

  static Set<String> getMixedInstancesPolicyFieldNames() {
    return ["onDemandAllocationStrategy", "onDemandBaseCapacity", "onDemandPercentageAboveBaseCapacity",
            "spotAllocationStrategy", "spotInstancePools", "launchTemplateOverridesForInstanceType"].toSet()
  }

  /**
   * Get all instance types in description.
   *
   * Why does this method exist?
   *      When launchTemplateOverrides are specified, either the overrides or instanceType is used,
   *      but all instance type inputs are returned by this method.
   * When is this method used?
   *      Used primarily for validation purposes, to ensure all instance types in request are compatible with
   *      other validated configuration parameters (to prevent ambiguity).
   *
   * @return all instance type(s)
   */
  Set<String> getAllInstanceTypes() {
    Set<String> instanceTypes = [instanceType]
    if (launchTemplateOverridesForInstanceType) {
      launchTemplateOverridesForInstanceType.each {
        instanceTypes << it.instanceType
      }
    }
    return instanceTypes
  }

  /**
   * Get allowed instance types in description. These are the instance types that an ASG can realistically launch.
   *
   * Why does this method exist?
   *      If launchTemplateOverrides are specified, they will override the same properties in launch template e.g. instanceType
   *      https://docs.aws.amazon.com/autoscaling/ec2/APIReference/API_LaunchTemplate.html.
   * When is this method used?
   *      Used for functional purposes and when the result is used for further actions like deriving certain defaults, whether to allow modifying cpu credit spec or not.
   *
   * @return allowed instance type(s)
   */
  Set<String> getAllowedInstanceTypes() {
    if (launchTemplateOverridesForInstanceType) {
      launchTemplateOverridesForInstanceType.collect{ it.instanceType }.toSet()
    } else {
      Collections.singleton(instanceType)
    }
  }
 // Launch Template features:end

  @Override
  Collection<String> getApplications() {
    return [application]
  }

  @Canonical
  static class Capacity {
    Integer min
    Integer max
    Integer desired
  }

  @Canonical
  static class Source {
    String account
    String region
    String asgName
    Boolean useSourceCapacity
  }

  @Canonical
  static class LaunchTemplatePlacement {
    String affinity
    String availabilityZone
    String groupName
    String hostId
    String tenancy
    String spreadDomain
    String hostResourceGroupArn
    Integer partitionNumber
  }

  @Canonical
  static class LaunchTemplateLicenseSpecification {
    String arn
  }

  /**
   * Support for multiple instance types.
   * This class encapsulates configuration mapped to a particular instance type.
   */
  @Canonical
  static class LaunchTemplateOverridesForInstanceType {
    /**
     * An instance type that is supported in the requested region and availability zone.
     * Required field when instanceTypeConfigOverrides is used.
     */
    String instanceType

    /**
     * The number of capacity units provided by {@link #instanceType} in terms of virtual CPUs, memory, storage, throughput, or other relative performance characteristic.
     * When an instance of type {@link #instanceType} is provisioned, it's capacity units count toward the desired capacity.
     */
    String weightedCapacity
  }
}
