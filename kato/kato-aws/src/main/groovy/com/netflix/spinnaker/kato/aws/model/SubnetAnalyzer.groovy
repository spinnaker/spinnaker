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
package com.netflix.spinnaker.kato.aws.model

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
   * Gets the identifiers of all the subnets in this set.
   *
   * @return the subnet ID strings
   */
  List<String> getSubnetIds() {
    allSubnets*.subnetId
  }

  /**
   * Simply finds a subnet based on its ID.
   *
   * @param id of the subnet
   * @return the unique subnet with that ID or null
   */
  SubnetData findSubnetById(String id) {
    Preconditions.checkNotNull(id)
    allSubnets.find { it.subnetId == id }
  }

  /**
   * Finds all subnets in a given VPC.
   *
   * @param vpcId id of the VPC the subnet belongs to
   * @return wrapped set of SubnetData representations of subnets associated with the specified VPC
   */
  SubnetAnalyzer findSubnetsByVpc(String vpcId) {
    Preconditions.checkNotNull(vpcId)
    new SubnetAnalyzer(allSubnets.findAll { it.vpcId == vpcId })
  }

  /**
   * Finds the subnet associated with the first Subnet ID. This is useful in cases where the attribute you care about
   * is guaranteed to be the same for all subnets.
   *
   * @param subnetIds Subnet IDs
   * @return the Subnet or null
   */
  SubnetData coerceLoneOrNoneFromIds(Collection<String> subnetIds) {
    subnetIds ? findSubnetById(subnetIds.iterator().next()?.trim()) : null
  }

  /**
   * Finds the identifier of the VPC indicated by the specified VPC Zone Identifier string.
   *
   * @param vpcZoneIdentifier the comma-delimited list of subnet IDs used in an ASG field as a roundabout way of
   *      indicating which VPC where the ASG launches instances
   * @return the identifier of the VPC where the subnets exist if available, or the default VPC if available, or null
   */
  String getVpcIdForVpcZoneIdentifier(String vpcZoneIdentifier) {
    List<String> subnetIds = subnetIdsFromVpcZoneIdentifier(vpcZoneIdentifier)
    coerceLoneOrNoneFromIds(subnetIds)?.vpcId ?: defaultVpcId
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
  List<String> getSubnetIdsForZones(Collection<String> zones, String purpose, SubnetTarget target = null) {
    Preconditions.checkNotNull(purpose)
    if (!zones) {
      return Collections.emptyList()
    }
    Function<SubnetData, String> purposeOfSubnet = { it.purpose } as Function
    Map<String, Collection<SubnetData>> zonesToSubnets = mapZonesToTargetSubnets(target).asMap()
    zonesToSubnets.subMap(zones).values().collect { Collection<SubnetData> subnetsForZone ->
      if (subnetsForZone == null) {
        return null
      }
      SubnetData subnetForPurpose = Maps.uniqueIndex(subnetsForZone, purposeOfSubnet)[purpose]
      subnetForPurpose?.subnetId
    }.findAll { it != null }
  }

  /**
   * Groups zones by subnet purposes they contain.
   *
   * @param allAvailabilityZones complete list of zones to group
   * @param target is the type of AWS object the subnet applies to (null means any object type)
   * @return zone name to subnet purposes, a null key indicates zones allowed for use outside of VPC
   */
  Map<String, Collection<String>> groupZonesByPurpose(Collection<String> allAvailabilityZones,
                                                      SubnetTarget target = null) {
    Multimap<String, String> zonesGroupedByPurpose = Multimaps.newSetMultimap([:], { [] as SortedSet } as Supplier)
    zonesGroupedByPurpose.putAll(null, allAvailabilityZones)
    allSubnets.each {
      if (it.availabilityZone in allAvailabilityZones && (!it.target || it.target == target)) {
        zonesGroupedByPurpose.put(it.purpose, it.availabilityZone)
      }
    }
    zonesGroupedByPurpose.keySet().inject([:]) { Map zoneListsByPurpose, String purpose ->
      zoneListsByPurpose[purpose] = zonesGroupedByPurpose.get(purpose) as List
      zoneListsByPurpose
    } as Map
  }

  /**
   * Finds all purposes across all specified zones for the specified target.
   *
   * @param zones the zones in AWS that you want purposes for
   * @param target is the type of AWS object the subnet applies to (null means any object type)
   * @return the set of distinct purposes or an empty Set
   */
  Set<String> getPurposesForZones(Collection<String> zones, SubnetTarget target = null) {
    if (!zones) {
      return Collections.emptySet()
    }
    Map<String, Collection<SubnetData>> zonesToSubnets = mapZonesToTargetSubnets(target).asMap()
    zones.inject([]) { Collection<String> allPurposes, String zone ->
      allPurposes + zonesToSubnets[zone].collect { it.purpose }
    } as Set
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

  /**
   * Constructs a new VPC Zone Identifier based on an existing VPC Zone Identifier and a list of zones.
   * A VPC Zone Identifier is really just a comma delimited list of subnet IDs.
   * I'm not happy that this method has to exist. It's just a wrapper around other methods that operate on a cleaner
   * abstraction without knowledge of the unfortunate structure of VPC Zone Identifier.
   *
   * @param vpcZoneIdentifier is used to derive a subnet purpose from
   * @param zones which the new VPC Zone Identifier will contain
   * @return a new VPC Zone Identifier or null if no purpose was derived
   */
  String constructNewVpcZoneIdentifierForZones(String vpcZoneIdentifier, List<String> zones) {
    if (!zones) {
      // No zones were selected because there was no chance to change them. Keep the VPC Zone Identifier.
      return vpcZoneIdentifier
    }
    String purpose = getPurposeFromVpcZoneIdentifier(vpcZoneIdentifier)
    constructNewVpcZoneIdentifierForPurposeAndZones(purpose, zones)
  }

  /**
   * Convert a VPC Zone Identifier into a list of subnet IDs.
   * A VPC Zone Identifier is really just a comma delimited list of subnet IDs.
   *
   * @param vpcZoneIdentifier the VPC Zone Identifier
   * @return list of subnet IDs
   */
  private List<String> subnetIdsFromVpcZoneIdentifier(String vpcZoneIdentifier) {
    vpcZoneIdentifier?.tokenize(',') ?: []
  }

  /**
   * Convert a list of subnet IDs into a VPC Zone Identifier.
   * A VPC Zone Identifier is really just a comma delimited list of subnet IDs.
   *
   * @param subnetIds the list of subnet IDs
   * @return the VPC Zone Identifier
   */
  private String vpcZoneIdentifierFromSubnetIds(List<String> subnetIds) {
    subnetIds.join(',')
  }

  /**
   * Figures out the subnet purpose given a VPC zone identifier.
   *
   * @param vpcZoneIdentifier is used to derive a subnet purpose from
   * @return the subnet purpose indicated by the vpcZoneIdentifier
   */
  String getPurposeFromVpcZoneIdentifier(String vpcZoneIdentifier) {
    List<String> oldSubnetIds = subnetIdsFromVpcZoneIdentifier(vpcZoneIdentifier)
    // All subnets used in the vpcZoneIdentifier will have the same subnet purpose if set up in Asgard.
    coerceLoneOrNoneFromIds(oldSubnetIds)?.purpose
  }

  /**
   * Constructs a new VPC Zone Identifier based on a subnet purpose and a list of zones.
   *
   * @param purpose is used to derive a subnet purpose from
   * @param zones which the new VPC Zone Identifier will contain
   * @return a new VPC Zone Identifier or null if no purpose was specified
   */
  String constructNewVpcZoneIdentifierForPurposeAndZones(String purpose, Collection<String> zones) {
    if (purpose) {
      List<String> newSubnetIds = getSubnetIdsForZones(zones, purpose, SubnetTarget.EC2) // This is only for ASGs.
      if (newSubnetIds) {
        return vpcZoneIdentifierFromSubnetIds(newSubnetIds)
      }
    }
    null
  }
}

