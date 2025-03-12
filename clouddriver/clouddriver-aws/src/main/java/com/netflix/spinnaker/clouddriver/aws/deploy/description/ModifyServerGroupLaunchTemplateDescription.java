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

package com.netflix.spinnaker.clouddriver.aws.deploy.description;

import com.amazonaws.util.CollectionUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice;
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride;
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupsNameable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * Description type that encapsulates properties associated with 1. EC2 launch template
 * (https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/ec2/model/ResponseLaunchTemplateData.html)
 * 2. Launch template overrides
 * (https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/autoscaling/model/LaunchTemplate.html)
 * 3. Instances distribution
 * (https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/autoscaling/model/InstancesDistribution.html)
 *
 * <p>Used to modify properties associated with the AWS entities listed above. Applicable to AWS
 * AutoScalingGroups backed by EC2 launch template with / without mixed instances policy
 * (https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/autoscaling/model/MixedInstancesPolicy.html)
 */
@Getter
@Setter
public class ModifyServerGroupLaunchTemplateDescription extends AbstractAmazonCredentialsDescription
    implements ServerGroupsNameable {
  String region;
  String asgName;
  String amiName;
  String instanceType;
  String subnetType;
  String iamRole;
  String keyPair;
  Boolean associatePublicIpAddress;
  String spotPrice;
  String ramdiskId;
  Boolean instanceMonitoring;
  Boolean ebsOptimized;
  String classicLinkVpcId;
  List<String> classicLinkVpcSecurityGroups;
  Boolean legacyUdf;
  String base64UserData;
  UserDataOverride userDataOverride = new UserDataOverride();
  List<AmazonBlockDevice> blockDevices;
  List<String> securityGroups;
  Boolean securityGroupsAppendOnly;

  /**
   * If false, the newly created server group will not pick up block device mapping customizations
   * from an ancestor group
   */
  boolean copySourceCustomBlockDeviceMappings = true;

  // Launch Template only fields
  private Boolean requireIMDV2;
  private String kernelId;
  private String imageId;
  private Boolean associateIPv6Address;
  private Boolean unlimitedCpuCredits;
  private Boolean enableEnclave;

  /**
   * Mixed Instances Policy properties.
   *
   * <p>Why are these properties here instead of {@link
   * com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyAsgDescription}?: Although most
   * of these properties are associated with the server group itself rather than it's launch
   * template, these properties are closely associated with and modify certain launch template
   * properties e.g. LaunchTemplateOverridesForInstanceType, spotPrice.
   */
  private String onDemandAllocationStrategy;

  private Integer onDemandBaseCapacity;
  private Integer onDemandPercentageAboveBaseCapacity;
  private String spotAllocationStrategy;
  private Integer spotInstancePools;
  private List<BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType>
      launchTemplateOverridesForInstanceType;

  @Override
  @JsonIgnore
  public Collection<String> getServerGroupNames() {
    return Collections.singletonList(asgName);
  }

  public static Set<String> getMetadataFieldNames() {
    return ImmutableSet.of(
        // read-only fields: serverGroupNames, applications
        "account",
        "region",
        "asgName",
        "credentials",
        "securityGroupsAppendOnly",
        "copySourceCustomBlockDeviceMappings");
  }

  public static Set<String> getMixedInstancesPolicyOnlyFieldNames() {
    return ImmutableSet.of(
        "onDemandAllocationStrategy",
        "onDemandBaseCapacity",
        "onDemandPercentageAboveBaseCapacity",
        "spotAllocationStrategy",
        "spotInstancePools",
        "launchTemplateOverridesForInstanceType");
  }

  public static Set<String> getMixedInstancesPolicyFieldNames() {
    return ImmutableSet.of(
        "onDemandAllocationStrategy",
        "onDemandBaseCapacity",
        "onDemandPercentageAboveBaseCapacity",
        "spotAllocationStrategy",
        "spotInstancePools",
        "spotPrice", // spotMaxPrice
        "launchTemplateOverridesForInstanceType");
  }

  /**
   * Get all instance types in description.
   *
   * <p>Why does this method exist? When launchTemplateOverrides are specified, either the overrides
   * or instanceType is used, but all instance type inputs are returned by this method. When is this
   * method used? Used primarily for validation purposes, to ensure all instance types in request
   * are compatible with other validated configuration parameters (to prevent ambiguity).
   *
   * @return all instance type(s)
   */
  public Set<String> getAllInstanceTypes() {
    Set instanceTypes = new HashSet();
    if (StringUtils.isNotBlank(this.getInstanceType())) {
      instanceTypes.add(this.getInstanceType());
    }
    if (!CollectionUtils.isNullOrEmpty(launchTemplateOverridesForInstanceType)) {
      launchTemplateOverridesForInstanceType.forEach(
          override -> instanceTypes.add(override.getInstanceType()));
    }
    return instanceTypes;
  }

  @Override
  public String toString() {
    return new StringBuilder("ModifyServerGroupLaunchTemplateDescription{")
        .append("region=" + region)
        .append(", asgName=" + asgName)
        .append(", amiName=" + amiName)
        .append(", instanceType=" + instanceType)
        .append(", subnetType=" + subnetType)
        .append(", iamRole=" + iamRole)
        .append(", keyPair=" + keyPair)
        .append(", associatePublicIpAddress=" + associatePublicIpAddress)
        .append(", spotPrice=" + spotPrice)
        .append(", ramdiskId=" + ramdiskId)
        .append(", instanceMonitoring=" + instanceMonitoring)
        .append(", ebsOptimized=" + ebsOptimized)
        .append(", classicLinkVpcId=" + classicLinkVpcId)
        .append(", classicLinkVpcSecurityGroups=" + classicLinkVpcSecurityGroups)
        .append(", legacyUdf=" + legacyUdf)
        .append(", base64UserData=" + base64UserData)
        .append(", userDataOverride=" + userDataOverride)
        .append(", blockDevices=" + blockDevices)
        .append(", securityGroups=" + securityGroups)
        .append(", securityGroupsAppendOnly=" + securityGroupsAppendOnly)
        .append(", copySourceCustomBlockDeviceMappings=" + copySourceCustomBlockDeviceMappings)
        .append(", requireIMDV2=" + requireIMDV2)
        .append(", kernelId=" + kernelId)
        .append(", imageId=" + imageId)
        .append(", associateIPv6Address=" + associateIPv6Address)
        .append(", unlimitedCpuCredits=" + unlimitedCpuCredits)
        .append(", onDemandAllocationStrategy=" + onDemandAllocationStrategy)
        .append(", onDemandBaseCapacity=" + onDemandBaseCapacity)
        .append(", onDemandPercentageAboveBaseCapacity=" + onDemandPercentageAboveBaseCapacity)
        .append(", spotAllocationStrategy=" + spotAllocationStrategy)
        .append(", spotInstancePools=" + spotInstancePools)
        .append(
            ", launchTemplateOverridesForInstanceType=" + launchTemplateOverridesForInstanceType)
        .append("}")
        .toString()
        .replaceAll(",\\s[a-zA-Z0-9]+=null", "");
  }
}
