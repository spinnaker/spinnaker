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

package com.netflix.spinnaker.kato.deploy.aws.ops

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.model.*
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.deploy.aws.description.UpsertSecurityGroupDescription
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

    def getSecurityGroup = { ->
      ec2.describeSecurityGroups(new DescribeSecurityGroupsRequest().withGroupNames(description.name))?.securityGroups?.getAt(0)
    }

    try {
      securityGroup = getSecurityGroup()
    } catch (AmazonServiceException ignore) {}

    if (!securityGroup) {
      def request = new CreateSecurityGroupRequest(description.name, description.description)
      if (description.vpcId) {
        request.withVpcId(description.vpcId)
      }
      ec2.createSecurityGroup(request)
      securityGroup = getSecurityGroup()
    }

    List<IpPermission> ipPermissions = description.securityGroupIngress.collect { ingress ->
      map(ingress).withUserIdGroupPairs(new UserIdGroupPair().withGroupName(ingress.name))
    } + description.ipIngress.collect { ingress ->
      map(ingress).withIpRanges(ingress.cidr)
    }

    if (securityGroup.ipPermissions) {
      def request = new RevokeSecurityGroupIngressRequest(description.name, securityGroup.ipPermissions.collect { it.userIdGroupPairs = it.userIdGroupPairs.collect { it.groupId = null; it }; it})
      ec2.revokeSecurityGroupIngress(request)
    }
    if (ipPermissions) {
      ec2.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest(description.name, ipPermissions))
    }
    null
  }

  static IpPermission map(UpsertSecurityGroupDescription.Ingress ingress) {
    new IpPermission().withIpProtocol(ingress.type.name()).withFromPort(ingress.startPort).withToPort(ingress.endPort)
  }
}
