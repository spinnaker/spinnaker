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
package com.netflix.spinnaker.clouddriver.aws.model

import com.amazonaws.services.ec2.model.Subnet
import com.google.common.base.Function
import com.google.common.base.Preconditions
import com.google.common.base.Supplier
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import groovy.transform.Canonical

/**
 * These are nontrivial queries we like to perform on subnets.
 */
@Canonical
class SubnetAnalyzer {
  /** All of the subnets contained in this object. */
  final Collection<SubnetData> allSubnets

  /** The identifier of the default VPC of the account-region, if available. */
  final String defaultVpcId

  private SubnetAnalyzer(Collection<SubnetData> allSubnets, String defaultVpcId = null) {
    this.defaultVpcId = defaultVpcId
    this.allSubnets = ImmutableSet.copyOf(allSubnets)
  }

  /**
   * Constructs Subnets from AWS Subnets.
   *
   * @param subnets the actual AWS Subnets
   * @param defaultVpcId the identifier of the default VPC, if available
   * @return a new immutable Subnets based off the subnets
   */
  public static SubnetAnalyzer from(Collection<Subnet> subnets, String defaultVpcId = null) {
    new SubnetAnalyzer(subnets.collect() { SubnetData.from(it) }, defaultVpcId)
  }

  /**
   * Finds the subnet IDs that map to specific zones
   *
   * @param zones the zones in AWS that you want Subnet IDs for
   * @param purpose only subnets with the specified purpose will be returned
   * @param target is the type of AWS object the subnet applies to (null means any object type)
   * @return the subnet IDs returned in the same order as the zones sent in or an empty List
   * @throws IllegalArgumentException if there are multiple subnets with the same purpose and zone
   */
  List<String> getSubnetIdsForZones(Collection<String> zones, String purpose, SubnetTarget target = null, Integer maxSubnetsPerZone = null) {
    Preconditions.checkNotNull(purpose)
    if (!zones) {
      return Collections.emptyList()
    }
    Map<String, Collection<SubnetData>> zonesToSubnets = mapZonesToTargetSubnets(target).asMap()
    zonesToSubnets.subMap(zones).findResults { z, c ->
      List<String> filtered = c.findResults { it.purpose == purpose ? it.subnetId : null }
      if (maxSubnetsPerZone != null) {
        Collections.shuffle(filtered)
        return filtered.take(maxSubnetsPerZone)
      }
      return filtered
    }.flatten()
  }

  private Multimap<String, SubnetData> mapZonesToTargetSubnets(SubnetTarget target) {
    Collection<SubnetData> targetSubnetsWithPurpose = allSubnets.findAll() {
      // Find ones with a purpose, and if they have a target then it should match
      (!it.target || it.target == target) && it.purpose
    }
    Multimaps.index(targetSubnetsWithPurpose, { it.availabilityZone } as Function)
  }

  /**
   * Finds a matching VPC identifier for the specified purpose, or null if there is no VPC ID match for that purpose.
   * If the purpose is null or an empty string, this method looks for default VPC ID if available.
   *
   * @param subnetPurpose the name of the purpose of the VPC
   * @return the identifier of the VPC that has the specified purpose, or the ID of the default VPC if the purpose
   *          specified is null or an empty string, or null if no matching VPC exists
   */
  String getVpcIdForSubnetPurpose(String subnetPurpose) {
    mapPurposeToVpcId()[subnetPurpose ?: null]
  }

  /**
   * Provides a one to one mapping from a Subnet purpose to its VPC ID. Purposes that span VPCs in the same region
   * are invalid and will be left out of the map.
   *
   * @return map of subnet purposes to their VPC ID
   */
  Map<String, String> mapPurposeToVpcId() {
    Map<String, List<SubnetData>> subnetsGroupedByPurpose = allSubnets.groupBy { it.purpose }
    subnetsGroupedByPurpose.inject([:]) { Map purposeToVpcId, Map.Entry entry ->
      String purpose = entry.key
      if (!purpose) {
        if (defaultVpcId) {
          purposeToVpcId[null] = defaultVpcId
        }
        return purposeToVpcId
      }
      List<SubnetData> subnets = entry.value as List
      Collection<String> distinctVpcIds = subnets*.vpcId.unique()
      // There should only be one VPC ID per purpose or the mapping from purpose back to VPC is ambiguous.
      // We just ignore purposes that are misconfigured so that the rest of the subnet purposes can be used.
      if (distinctVpcIds.size() == 1) {
        purposeToVpcId[purpose] = distinctVpcIds.iterator().next()
      }
      purposeToVpcId
    } as Map
  }
}
