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

import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.IpRange
import com.amazonaws.services.ec2.model.Ipv6Range
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.UserIdGroupPair
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
      IpPermission permission = new IpPermission(ipProtocol: ingress.ipProtocol, fromPort: ingress.startPort, toPort: ingress.endPort)
      if (ingress.cidr?.contains(':')) {
        permission.ipv6Ranges = [new Ipv6Range().withCidrIpv6(ingress.cidr).withDescription(ingress.description)]
      } else {
        permission.ipv4Ranges = [new IpRange().withCidrIp(ingress.cidr).withDescription(ingress.description)]
      }
      permission
    }
    description.securityGroupIngress.each { ingress ->
      final accountName = ingress.accountName ?: description.account
      final accountId = ingress.accountId ?: securityGroupLookup.getAccountIdForName(accountName)
      final vpcId = ingress.vpcId ?: description.vpcId
      def newUserIdGroupPair = null
      if (ingress.id) {
        newUserIdGroupPair = new UserIdGroupPair(userId: accountId, groupId: ingress.id, vpcId: ingress.vpcId)
      } else {
        final ingressSecurityGroup = securityGroupLookup.getSecurityGroupByName(accountName, ingress.name, vpcId)
        if (ingressSecurityGroup.present) {
          final groupId = ingressSecurityGroup.get().getSecurityGroup().groupId
          newUserIdGroupPair = new UserIdGroupPair(userId: accountId, groupId: groupId, vpcId: ingress.vpcId)
        } else {
          if (description.vpcId) {
            missing.add(ingress)
          } else {
            newUserIdGroupPair = new UserIdGroupPair(userId: accountId, groupName: ingress.name)
          }
        }
      }

      if (newUserIdGroupPair) {
        def newIpPermission = new IpPermission(ipProtocol: ingress.ipProtocol, fromPort: ingress.startPort,
          toPort: ingress.endPort, userIdGroupPairs: [newUserIdGroupPair])
        ipPermissions.add(newIpPermission)
      }
    }
    new ConvertedIngress(ipPermissions, new MissingSecurityGroups(
      all: missing,
      selfReferencing: missing.findAll { it.name == description.name && it.accountName == description.account }
    ))
  }

  static List<IpPermission> flattenPermissions(SecurityGroup securityGroup) {
    Collection<IpPermission> ipPermissions = securityGroup.ipPermissions
    ipPermissions.collect { IpPermission ipPermission ->
      ipPermission.userIdGroupPairs.collect {
        it.groupName = null
        it.peeringStatus = null
        it.vpcPeeringConnectionId = null
        new IpPermission()
          .withFromPort(ipPermission.fromPort)
          .withToPort(ipPermission.toPort)
          .withIpProtocol(ipPermission.ipProtocol)
          .withUserIdGroupPairs(it)
      } + ipPermission.ipv4Ranges.collect {
        new IpPermission()
          .withFromPort(ipPermission.fromPort)
          .withToPort(ipPermission.toPort)
          .withIpProtocol(ipPermission.ipProtocol)
          .withIpv4Ranges(it)
      } + ipPermission.ipv6Ranges.collect {
        new IpPermission()
          .withFromPort(ipPermission.fromPort)
          .withToPort(ipPermission.toPort)
          .withIpProtocol(ipPermission.ipProtocol)
          .withIpv6Ranges(it)
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
    List<IpPermission> filteredNewList = newList.findAll { ipPermission -> ipPermission.userIdGroupPairs.isEmpty() }
    List<IpPermission> filteredExistingRuleList = existingRules.findAll { existingRule -> existingRule.userIdGroupPairs.isEmpty()}
    filteredNewList.forEach({ newListEntry ->
      IpPermission match = findIpPermission(filteredExistingRuleList, newListEntry)
      if (match) {
        if (newListEntry.ipv4Ranges.collect { it.description }.any()
          || newListEntry.ipv6Ranges.collect { it.description }.any()) {
          tobeUpdated.add(newListEntry) // matches old rule , needs an update for description
        }
        filteredExistingRuleList.remove(match) // remove from future processing
      } else {
        tobeAdded.add(newListEntry) //no match in old rule so must be added
      }
    })
    tobeRemoved = filteredExistingRuleList // rules that needs to be removed
    return new IpRuleDelta(tobeAdded, tobeRemoved , tobeUpdated)
  }

  static IpPermission findIpPermission(List<IpPermission> existingList, IpPermission ipPermission) {
    existingList.find { it ->
      (((it.ipv4Ranges.collect { it.cidrIp }.sort() == ipPermission.ipv4Ranges.collect { it.cidrIp }.sort()
        && it.fromPort == ipPermission.fromPort
        && it.toPort == ipPermission.toPort
        && it.ipProtocol == ipPermission.ipProtocol) && !ipPermission.ipv4Ranges.isEmpty())
        || ((it.ipv6Ranges.collect { it.cidrIpv6 }.sort() == ipPermission.ipv6Ranges.collect { it.cidrIpv6 }.sort()
        && it.fromPort == ipPermission.fromPort
        && it.toPort == ipPermission.toPort
        && it.ipProtocol == ipPermission.ipProtocol) && !ipPermission.ipv6Ranges.isEmpty()))
    }
  }

  static List<IpPermission> userIdGroupPairsDiff(List<IpPermission> converted, List<IpPermission> existingIpPermissions) {
    List<IpPermission> convertedFromDesc = converted.findAll { elements -> elements.userIdGroupPairs.size() != 0 }
    List<IpPermission> existing = existingIpPermissions.findAll { elements -> elements.userIdGroupPairs.size() != 0 }
    return convertedFromDesc - existing
  }

  @Canonical
  static class IpRuleDelta {
    List<IpPermission> toAdd
    List<IpPermission> toRemove
    List<IpPermission> toUpdate
  }

}
