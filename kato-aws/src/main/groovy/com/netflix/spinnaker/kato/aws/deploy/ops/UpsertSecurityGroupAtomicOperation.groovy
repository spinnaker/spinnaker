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

package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class UpsertSecurityGroupAtomicOperation implements AtomicOperation<Void> {
  final UpsertSecurityGroupDescription description

  UpsertSecurityGroupAtomicOperation(UpsertSecurityGroupDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  Void operate(List priorOutputs) {
    def ec2 = amazonClientProvider.getAmazonEC2(description.credentials, description.region)
    SecurityGroup securityGroup

    final List<SecurityGroup> securityGroups = ec2.describeSecurityGroups().securityGroups.
            findAll { it.vpcId == description.vpcId }
    securityGroup = securityGroups.find { it.groupName == description.name }

    List<IpPermission> ipPermissions = description.securityGroupIngress.collect { ingress ->
      def ingressSecurityGroup = securityGroups.find { it.groupName == ingress.name }
      map(ingress).withUserIdGroupPairs(new UserIdGroupPair().withGroupId(ingressSecurityGroup.groupId))
    } + description.ipIngress.collect { ingress ->
      map(ingress).withIpRanges(ingress.cidr)
    }

    String groupId
    if (!securityGroup) {
      def request = new CreateSecurityGroupRequest(description.name, description.description)
      if (description.vpcId) {
        request.withVpcId(description.vpcId)
      }
      def result = ec2.createSecurityGroup(request)
      groupId = result.groupId
    } else {
      groupId = securityGroup.groupId
      // Remove existing permissions if updating
      def existingPermissions = securityGroup.ipPermissions.collect {
        // Remove group name because AWS gets confused when it is provided in addition to the ID
        it.userIdGroupPairs = it.userIdGroupPairs.collect { it.groupName = null; it }
        it
      }
      if (securityGroup.ipPermissions) {
        def request = new RevokeSecurityGroupIngressRequest(groupId: securityGroup.groupId,
                ipPermissions: existingPermissions
        )
        ec2.revokeSecurityGroupIngress(request)
      }
    }
    if (ipPermissions) {
      ec2.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest(groupId: groupId,
              ipPermissions: ipPermissions))
    }
    null
  }

  static IpPermission map(UpsertSecurityGroupDescription.Ingress ingress) {
    new IpPermission().withIpProtocol(ingress.type.name()).withFromPort(ingress.startPort).withToPort(ingress.endPort)
  }
}
