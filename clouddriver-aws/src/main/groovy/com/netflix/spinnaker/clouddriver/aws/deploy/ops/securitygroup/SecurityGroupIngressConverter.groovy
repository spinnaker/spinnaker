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
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import groovy.transform.Canonical
import groovy.transform.Immutable

@Canonical
class SecurityGroupIngressConverter {

  @Immutable
  static class ConvertedIngress {
    List<IpPermission> converted
    List<UpsertSecurityGroupDescription.SecurityGroupIngress> missingSecurityGroups
  }

  static ConvertedIngress convertIngressToIpPermissions(SecurityGroupLookupFactory.SecurityGroupLookup securityGroupLookup,
                                                 UpsertSecurityGroupDescription description) {
    List<UpsertSecurityGroupDescription.SecurityGroupIngress> missing = []
    List<IpPermission> ipPermissions = description.ipIngress.collect { ingress ->
      new IpPermission(ipProtocol: ingress.ipProtocol, fromPort: ingress.startPort, toPort: ingress.endPort,
        ipRanges: [ingress.cidr])
    }
    description.securityGroupIngress.each { ingress ->
      final accountName = ingress.accountName ?: description.credentialAccount
      final accountId = securityGroupLookup.getAccountIdForName(accountName)
      final vpcId = ingress.vpcId ?: description.vpcId
      def newUserIdGroupPair = null
      if (ingress.id) {
        newUserIdGroupPair = new UserIdGroupPair(userId: accountId, groupId: ingress.id)
      } else {
          final ingressSecurityGroup = securityGroupLookup.getSecurityGroupByName(accountName, ingress.name, vpcId)
          if (ingressSecurityGroup.present) {
            final groupId = ingressSecurityGroup.get().getSecurityGroup().groupId
            newUserIdGroupPair = new UserIdGroupPair(userId: accountId, groupId: groupId)
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
    new ConvertedIngress(ipPermissions, missing)
  }

  static List<IpPermission> flattenPermissions(Collection<IpPermission> ipPermissions) {
    ipPermissions.collect { IpPermission ipPermission ->
      ipPermission.userIdGroupPairs.collect {
        it.groupName = null
        it.vpcId = null
        it.peeringStatus = null
        it.vpcPeeringConnectionId = null
        new IpPermission()
          .withFromPort(ipPermission.fromPort)
          .withToPort(ipPermission.toPort)
          .withIpProtocol(ipPermission.ipProtocol)
          .withUserIdGroupPairs(it)
      } + ipPermission.ipRanges.collect {
        new IpPermission()
          .withFromPort(ipPermission.fromPort)
          .withToPort(ipPermission.toPort)
          .withIpProtocol(ipPermission.ipProtocol)
          .withIpRanges(it)
      }
    }.flatten().unique()
  }
}
