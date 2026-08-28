/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup

import software.amazon.awssdk.services.ec2.model.IpPermission
import software.amazon.awssdk.services.ec2.model.IpRange
import software.amazon.awssdk.services.ec2.model.Ipv6Range
import software.amazon.awssdk.services.ec2.model.SecurityGroup
import software.amazon.awssdk.services.ec2.model.UserIdGroupPair
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription.SecurityGroupIngress
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup
import groovy.transform.Canonical
import groovy.transform.Immutable

@Canonical
class SecurityGroupIngressConverter {

  @Immutable
  static class ConvertedIngress {
    List<IpPermission> converted
    MissingSecurityGroups missingSecurityGroups
  }

  @Immutable
  static class MissingSecurityGroups {
    List<SecurityGroupIngress> all
    List<SecurityGroupIngress> selfReferencing

    boolean anyMissing(boolean ignoreSelfReferencing) {
      if (all.isEmpty()) {
        return false;
      } else if (ignoreSelfReferencing) {
        return all.size() > selfReferencing.size()
      }
      return true
    }

    boolean hasMissingNonSelfReferencingGroups() {
      return !all.isEmpty() && all.size() > selfReferencing.size()
    }
  }

  static ConvertedIngress convertIngressToIpPermissions(SecurityGroupLookup securityGroupLookup,
                                                        UpsertSecurityGroupDescription description) {
    List<SecurityGroupIngress> missing = []
    List<IpPermission> ipPermissions = description.ipIngress.collect { ingress ->
      def permissionBuilder = IpPermission.builder().ipProtocol(ingress.ipProtocol).fromPort(ingress.startPort).toPort(ingress.endPort)
      if (ingress.cidr?.contains(':')) {
        permissionBuilder.ipv6Ranges([Ipv6Range.builder().cidrIpv6(ingress.cidr).description(ingress.description).build()])
      } else {
        permissionBuilder.ipRanges([IpRange.builder().cidrIp(ingress.cidr).description(ingress.description).build()])
      }
      permissionBuilder.build()
    }
    description.securityGroupIngress.each { ingress ->
      final accountName = ingress.accountName ?: description.account
      final accountId = ingress.accountId ?: securityGroupLookup.getAccountIdForName(accountName)
      final vpcId = ingress.vpcId ?: description.vpcId
      def newUserIdGroupPair = null
      if (ingress.id) {
        newUserIdGroupPair = UserIdGroupPair.builder().userId(accountId).groupId(ingress.id).vpcId(ingress.vpcId).build()
      } else {
        final ingressSecurityGroup = securityGroupLookup.getSecurityGroupByName(accountName, ingress.name, vpcId)
        if (ingressSecurityGroup.present) {
          final groupId = ingressSecurityGroup.get().getSecurityGroup().groupId()
          newUserIdGroupPair = UserIdGroupPair.builder().userId(accountId).groupId(groupId).vpcId(ingress.vpcId).build()
        } else {
          if (description.vpcId) {
            missing.add(ingress)
          } else {
            newUserIdGroupPair = UserIdGroupPair.builder().userId(accountId).groupName(ingress.name).build()
          }
        }
      }

      if (newUserIdGroupPair) {
        def newIpPermission = IpPermission.builder().ipProtocol(ingress.ipProtocol).fromPort(ingress.startPort)
          .toPort(ingress.endPort).userIdGroupPairs([newUserIdGroupPair]).build()
        ipPermissions.add(newIpPermission)
      }
    }
    new ConvertedIngress(ipPermissions, new MissingSecurityGroups(
      all: missing,
      selfReferencing: missing.findAll { it.name == description.name && it.accountName == description.account }
    ))
  }

  static List<IpPermission> flattenPermissions(SecurityGroup securityGroup) {
    Collection<IpPermission> ipPermissions = securityGroup.ipPermissions()
    ipPermissions.collect { IpPermission ipPermission ->
      ipPermission.userIdGroupPairs().collect {
        def cleaned = it.toBuilder().groupName(null).peeringStatus(null).vpcPeeringConnectionId(null).build()
        IpPermission.builder()
          .fromPort(ipPermission.fromPort())
          .toPort(ipPermission.toPort())
          .ipProtocol(ipPermission.ipProtocol())
          .userIdGroupPairs(cleaned)
          .build()
      } + ipPermission.ipRanges().collect {
        IpPermission.builder()
          .fromPort(ipPermission.fromPort())
          .toPort(ipPermission.toPort())
          .ipProtocol(ipPermission.ipProtocol())
          .ipRanges(it)
          .build()
      } + ipPermission.ipv6Ranges().collect {
        IpPermission.builder()
          .fromPort(ipPermission.fromPort())
          .toPort(ipPermission.toPort())
          .ipProtocol(ipPermission.ipProtocol())
          .ipv6Ranges(it)
          .build()
      }
    }.flatten().unique()
  }

  /**
   *
   * @param newList from description
   * @param existingRules
   * @return Map of rules that needs to be added , removed and updated
   * Computes the delta between the existing rules and new rule
   * Any rule present in description and not in the existing rule gets added to addition list.
   * Any rule in description but present in existing rule get added to the remove list.
   * Any rule with a change in description only gets added to the update list based on the following,
   * - If a new rule has description value add it to update list to make it consistent.
   * - If new rule has no description value set, ignore.
   */
  static IpRuleDelta computeIpRuleDelta(List<IpPermission> newList, List<IpPermission> existingRules) {
    List<IpPermission> tobeAdded = new ArrayList<>()
    List<IpPermission> tobeRemoved = new ArrayList<>()
    List<IpPermission> tobeUpdated = new ArrayList<>()
    List<IpPermission> filteredNewList = newList.findAll { ipPermission -> ipPermission.userIdGroupPairs().isEmpty() }
    List<IpPermission> filteredExistingRuleList = existingRules.findAll { existingRule -> existingRule.userIdGroupPairs().isEmpty()}
    filteredNewList.forEach({ newListEntry ->
      IpPermission match = findIpPermission(filteredExistingRuleList, newListEntry)
      if (match) {
        if (newListEntry.ipRanges().collect { it.description }.any()
          || newListEntry.ipv6Ranges().collect { it.description }.any()) {
          tobeUpdated.add(newListEntry) // matches old rule , needs an update for description
        }
        filteredExistingRuleList.remove(match) // remove from future processing
      } else {
        tobeAdded.add(newListEntry) //no match in old rule so must be added
      }
    })
    tobeRemoved = filteredExistingRuleList // rules that needs to be removed
    return new IpRuleDelta(tobeAdded, tobeRemoved, tobeUpdated)
  }

  static IpPermission findIpPermission(List<IpPermission> existingList, IpPermission ipPermission) {
    existingList.find { it ->
      (((it.ipRanges().collect { it.cidrIp }.sort() == ipPermission.ipRanges().collect { it.cidrIp }.sort()
        && it.fromPort() == ipPermission.fromPort()
        && it.toPort() == ipPermission.toPort()
        && it.ipProtocol() == ipPermission.ipProtocol()) && !ipPermission.ipRanges().isEmpty())
        || ((it.ipv6Ranges().collect { it.cidrIpv6 }.sort() == ipPermission.ipv6Ranges().collect { it.cidrIpv6 }.sort()
        && it.fromPort() == ipPermission.fromPort()
        && it.toPort() == ipPermission.toPort()
        && it.ipProtocol() == ipPermission.ipProtocol()) && !ipPermission.ipv6Ranges().isEmpty()))
    }
  }

  /**
   *
   * @param newList from description
   * @param existingRules
   * @return Map of rules that needs to be added , removed and updated
   * Computes the delta between the existing rules and new rule
   * Any rule present in description and not in the existing rule gets added to addition list.
   * Any rule not present in description but present in existing rule get added to the remove list.
   * Any rule with a change in description only gets added to the update list based on the following,
   * - If a new rule has description value add it to update list to make it consistent.
   * - If new rule has no description value set, ignore.
   */
  static UserIdGroupPairsDelta computeUserIdGroupPairsDelta(List<IpPermission> newList, List<IpPermission> existingRules) {
    List<IpPermission> tobeAdded = new ArrayList<>()
    List<IpPermission> tobeRemoved = new ArrayList<>()
    List<IpPermission> tobeUpdated = new ArrayList<>()
    List<IpPermission> filteredNewList = newList.findAll { ipPermission -> ipPermission.userIdGroupPairs.size() != 0 }
    List<IpPermission> filteredExistingRuleList = existingRules.findAll { existingRule -> existingRule.userIdGroupPairs.size() != 0 }
    filteredNewList.forEach({ newListEntry ->
      IpPermission match = findUserIdGroupPermission(filteredExistingRuleList, newListEntry)
      if (match) {
        if (newListEntry.userIdGroupPairs.collect { it.description }.any()) {
          tobeUpdated.add(newListEntry) // matches old rule , needs an update for description
        }
        filteredExistingRuleList.remove(match) // remove from future processing
      } else {
        tobeAdded.add(newListEntry) //no match in old rule so must be added
      }
    })
    tobeRemoved = filteredExistingRuleList // rules that needs to be removed
    return new UserIdGroupPairsDelta(tobeAdded, tobeRemoved, tobeUpdated)
  }

  static IpPermission findUserIdGroupPermission(List<IpPermission> existingList, IpPermission ipPermission) {
    existingList.find { it ->
      (it.userIdGroupPairs.collect { it.groupId }.sort() == ipPermission.userIdGroupPairs.collect { it.groupId }.sort()
        && it.userIdGroupPairs.collect { it.userId }.sort() == ipPermission.userIdGroupPairs.collect { it.userId }.sort()
        && it.fromPort == ipPermission.fromPort
        && it.toPort == ipPermission.toPort
        && it.ipProtocol == ipPermission.ipProtocol)
    }
  }

  @Canonical
  static class IpRuleDelta {
    List<IpPermission> toAdd
    List<IpPermission> toRemove
    List<IpPermission> toUpdate
  }

  @Canonical
  static class UserIdGroupPairsDelta {
    List<IpPermission> toAdd
    List<IpPermission> toRemove
    List<IpPermission> toUpdate
  }

}
