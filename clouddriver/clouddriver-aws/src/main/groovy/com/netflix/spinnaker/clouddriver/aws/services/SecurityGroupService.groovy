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
package com.netflix.spinnaker.clouddriver.aws.services

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.Filter
import com.netflix.spinnaker.clouddriver.aws.model.SecurityGroupNotFoundException
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer

import java.util.regex.Pattern

class SecurityGroupService {
  private static final Pattern SG_PATTERN = Pattern.compile(/^sg-[0-9a-f]+$/)

  private final AmazonEC2 amazonEC2
  private final SubnetAnalyzer subnetAnalyzer

  SecurityGroupService(AmazonEC2 amazonEC2, SubnetAnalyzer subnetAnalyzer) {
    this.amazonEC2 = amazonEC2
    this.subnetAnalyzer = subnetAnalyzer
  }
/**
 * Find a security group that matches the name of this application.
 *
 * @param applicationName the name of the application to lookup
 * @param subnetPurpose the subnet within which the lookup should take place
 * @return id of Security Group for application
 */
  String getSecurityGroupForApplication(String applicationName, String subnetPurpose = null) {
    try {
      getSecurityGroupIdsWithSubnetPurpose([applicationName], subnetPurpose)?.values()?.getAt(0)
    } catch (SecurityGroupNotFoundException ignore) {
      null
    }
  }

  /**
   * Find security group ids for provided security group names
   *
   * @param securityGroupNames
   * @return Map of security group ids keyed by corresponding security group name
   */
  Map<String, String> getSecurityGroupIds(Collection<String> securityGroupNames, String vpcId = null, boolean failIfNotAllResolved = true) {
    if (!securityGroupNames) { return [:] }
    def request = new DescribeSecurityGroupsRequest()
      .withFilters(new Filter("group-name", securityGroupNames.toList()))
    DescribeSecurityGroupsResult result = amazonEC2.describeSecurityGroups(request)
    Map<String, String> securityGroups = result
      .securityGroups
      .findAll { it.vpcId == vpcId }
      .collectEntries { [(it.groupName): it.groupId] }
    if (failIfNotAllResolved && !securityGroups.keySet().containsAll(securityGroupNames)) {
      def missingGroups = securityGroupNames - securityGroups.keySet()
      def ex = new SecurityGroupNotFoundException("Missing security groups: ${missingGroups.join(',')}")
      ex.missingSecurityGroups = missingGroups
      throw ex
    }
    securityGroups
  }

  /**
   * Find security group ids for provided security group names
   * @param securityGroupNames names to resolve to ids
   * @param subnetPurpose if not null, will find the vpcId matching the subnet purpose and locate groups in that vpc
   * @return group ids
   */
  Map<String, String> getSecurityGroupIdsWithSubnetPurpose(Collection<String> securityGroupNames, String subnetPurpose = null) {
    String vpcId = subnetPurpose == null ? null : subnetAnalyzer.getVpcIdForSubnetPurpose(subnetPurpose)
    getSecurityGroupIds(securityGroupNames, vpcId)
  }

  /**
   * Create a security group for this application. Security Group name will equal the application's.
   * (ie. "application") name.
   *
   * @param applicationName
   * @param subnetPurpose
   * @return id of the security group created
   */
  String createSecurityGroup(String applicationName, String subnetPurpose = null) {
    createSecurityGroupWithVpcId(applicationName, subnetPurpose ? subnetAnalyzer.getVpcIdForSubnetPurpose(subnetPurpose) : null)
  }

  /**
   * Create a security group using the vpcId specified for this application. Security Group name will equal the application's.
   * (ie. "application") name.
   *
   * @param applicationName
   * @param vpcId
   * @return id of the security group created
   */
  String createSecurityGroupWithVpcId(String applicationName, String vpcId) {
    CreateSecurityGroupRequest request = new CreateSecurityGroupRequest(applicationName, "Security Group for $applicationName")
    if (vpcId) {
      request.withVpcId(vpcId)
    }
    CreateSecurityGroupResult result = amazonEC2.createSecurityGroup(request)
    result.groupId
  }

  Map<String, String> getSecurityGroupNamesFromIds(Collection<String> securityGroupIds) {
    if (!securityGroupIds) {
      return [:]
    }
    def groupIds = new HashSet<>(securityGroupIds)
    def groups = amazonEC2.describeSecurityGroups(new DescribeSecurityGroupsRequest().withGroupIds(groupIds)).securityGroups
    if (groups.size() != groupIds.size()) {
      def missing = groupIds.findAll { id -> !groups.find { it.groupId == id }}
      throw new SecurityGroupNotFoundException("Failed to find groups ${missing}")
    }
    return groups.collectEntries {
      [(it.groupName): it.groupId]
    } ?: [:]
  }

  List<String> resolveSecurityGroupNamesByStrategy(List<String> securityGroupNamesAndIds,
                                                   Closure<Map<String, String>> idResolver) {
    if (securityGroupNamesAndIds) {
      Collection<String> ids = securityGroupNamesAndIds.toSet()
      Collection<String> names = ids.findAll { !SG_PATTERN.matcher(it).matches() } as Set<String>
      ids.removeAll(names)
      if (ids) {
        Map<String, String> resolvedNames = idResolver.call(ids.toList())
        names.addAll(resolvedNames.keySet())
      }
      return names.toList()
    } else {
      return []
    }
  }

  List<String> resolveSecurityGroupIdsByStrategy(List<String> securityGroupNamesAndIds,
                                                 Closure<Map<String, String>> nameResolver) {
    if (securityGroupNamesAndIds) {
      Collection<String> names = securityGroupNamesAndIds.toSet()
      Collection<String> ids = names.findAll { SG_PATTERN.matcher(it).matches() } as Set<String>
      names.removeAll(ids)
      if (names) {
        def resolvedIds = nameResolver.call(names.toList())
        ids.addAll(resolvedIds.values())
      }
      return ids.toList()
    } else {
      return []
    }
  }

  List<String> resolveSecurityGroupIdsWithSubnetType(List<String> securityGroupNamesAndIds, String subnetPurpose) {
    return this.resolveSecurityGroupIdsByStrategy(securityGroupNamesAndIds) { List<String> names ->
      this.getSecurityGroupIdsWithSubnetPurpose(names, subnetPurpose)
    }
  }

  List<String> resolveSecurityGroupIdsInVpc(List<String> securityGroupNamesAndIds, String vpcId) {
    return this.resolveSecurityGroupIdsByStrategy(securityGroupNamesAndIds) { List<String> names ->
      this.getSecurityGroupIds(names, vpcId)
    }
  }
}
