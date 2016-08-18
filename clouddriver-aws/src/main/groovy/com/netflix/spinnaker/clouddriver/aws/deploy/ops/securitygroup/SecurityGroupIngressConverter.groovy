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
    List<SecurityGroupIngress> missingSecurityGroups
  }

  static ConvertedIngress convertIngressToIpPermissions(SecurityGroupLookup securityGroupLookup,
                                                        UpsertSecurityGroupDescription description) {
    List<SecurityGroupIngress> missing = []
    List<IpPermission> ipPermissions = description.ipIngress.collect { ingress ->
      new IpPermission(ipProtocol: ingress.ipProtocol, fromPort: ingress.startPort, toPort: ingress.endPort,
        ipRanges: [ingress.cidr])
    }
    description.securityGroupIngress.each { ingress ->
      // When converting ingress we require that we can resolve the accountId for that ingress.
      // This means it either has to be explicitly provided (in which case we prefer it, and use
      // it to attempt to look up accountName) or it has to be an accountName that Spinnaker
      // knows about, in which case we will use that to find the accountId.
      //
      // Not finding the name is ok - it means we are dealing with a cross account ingress to
      // an account Spinnaker does not manage, but it does mean that the ingress permission also
      // has to include all the required fields (either name for ec2 classic, or vpcId + groupId
      // for ec2-vpc) because we can't look up the group to determine those values

      String accountId = null
      String accountName = null
      if (ingress.accountId) {
        accountId = ingress.accountId
        accountName = securityGroupLookup.accountIdExists(accountId) ? securityGroupLookup.getAccountNameForId(accountId) : null
      } else {
        def creds = securityGroupLookup.getCredentialsForName(ingress.accountName ?: description.credentialAccount)
        if (creds) {
          accountName = creds.name
          accountId = creds.accountId
        }
      }

      if (!accountId) {
        missing.add(ingress)
      } else {
        final groupVpcId = description.vpcId
        //when the ingress permission references a security group in the same vpc, the UserIdGroupPair has it as null
        final ingressVpcId = ingress.vpcId == groupVpcId ? null : ingress.vpcId
        def newUserIdGroupPair = null
        if (ingress.id) {
          newUserIdGroupPair = new UserIdGroupPair(userId: accountId, groupId: ingress.id, vpcId: ingressVpcId)
        } else {
          //we need name at this point to try to resolve id
          if (!ingress.name) {
            missing.add(ingress)
          } else {
            Optional<SecurityGroupLookupFactory.SecurityGroupUpdater> ingressSecurityGroup = accountName ?
              securityGroupLookup.getSecurityGroupByName(accountName, ingress.name, ingressVpcId ?: groupVpcId) :
              Optional.empty()

            if (ingressSecurityGroup.present) {
              final groupId = ingressSecurityGroup.get().getSecurityGroup().groupId
              newUserIdGroupPair = new UserIdGroupPair(userId: accountId, groupId: groupId, vpcId: ingressVpcId)
            } else {
              //if we are in vpc, then we need to have resolved the name to id
              if (groupVpcId) {
                missing.add(ingress)
              } else {
                //in ec2 classic we can use accountId+name to grant permission. this could be valid for
                // specifing a group in an account spinnaker doesn't manage
                newUserIdGroupPair = new UserIdGroupPair(userId: accountId, groupName: ingress.name)
              }
            }
          }
        }

        if (newUserIdGroupPair) {
          def newIpPermission = new IpPermission(ipProtocol: ingress.ipProtocol, fromPort: ingress.startPort,
            toPort: ingress.endPort, userIdGroupPairs: [newUserIdGroupPair])
          ipPermissions.add(newIpPermission)
        }
      }
    }
    new ConvertedIngress(ipPermissions, missing)
  }

  static List<IpPermission> flattenPermissions(SecurityGroup securityGroup) {
    Collection<IpPermission> ipPermissions = securityGroup.ipPermissions
    ipPermissions.collect { IpPermission ipPermission ->
      ipPermission.userIdGroupPairs.collect {
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
