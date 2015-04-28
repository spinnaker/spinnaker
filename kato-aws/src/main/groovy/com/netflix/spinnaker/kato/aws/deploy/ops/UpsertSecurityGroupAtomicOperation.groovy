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

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.google.common.annotations.VisibleForTesting
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import groovy.transform.PackageScope
import org.springframework.beans.factory.annotation.Autowired

class UpsertSecurityGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_SG"

  final UpsertSecurityGroupDescription description

  UpsertSecurityGroupAtomicOperation(UpsertSecurityGroupDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

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
    List<IpPermission> ipPermissionsToRemove = []

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

      ipPermissions = ipPermissions.collect {
        // Ensure supplied permissions have an appropriate userId that can be subsequently .contains()'d against
        // existing permissions on the target security group
        it.userIdGroupPairs = it.userIdGroupPairs.collect {
          it.userId = it.userId ?: securityGroup.ownerId
          it
        }
        it
      }

      def existingIpPermissions = securityGroup.ipPermissions.collect { IpPermission ipPermission ->
        ipPermission.userIdGroupPairs.collect {
          it.groupName = null
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
      }.flatten()

      ipPermissionsToRemove = existingIpPermissions.findAll {
        // existed previously but were not supplied in upsert and should be deleted
        !ipPermissions.contains(it)
      }
      ipPermissions.removeAll(ipPermissionsToRemove)

      // no need to recreate existing permissions
      ipPermissions.removeAll(existingIpPermissions)
    }

    ipPermissions.each {
      try {
        ec2.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest(
          groupId: groupId,
          ipPermissions: [it]
        ))
        task.updateStatus BASE_PHASE, "Permission added to ${description.name} (${it})."
      } catch (AmazonServiceException e) {
        if (e.errorCode == "InvalidPermission.Duplicate") {
          task.updateStatus BASE_PHASE, "Permission already exists on ${description.name} (${it})."
          return
        }

        throw e
      }
    }

    filterUnsupportedRemovals(securityGroup, ipPermissionsToRemove).each {
      def request = new RevokeSecurityGroupIngressRequest(
        groupId: securityGroup.groupId,
        ipPermissions: [it]
      )
      ec2.revokeSecurityGroupIngress(request)
      task.updateStatus BASE_PHASE, "Permission removed from ${description.name} (${it.toString()})."
    }

    null
  }

  static IpPermission map(UpsertSecurityGroupDescription.Ingress ingress) {
    new IpPermission().withIpProtocol(ingress.type.name()).withFromPort(ingress.startPort).withToPort(ingress.endPort)
  }

  @VisibleForTesting
  @PackageScope
  List<IpPermission> filterUnsupportedRemovals(SecurityGroup securityGroup, List<IpPermission> ipPermissions) {
    return ipPermissions.findAll {
      if (it.ipRanges) {
        task.updateStatus BASE_PHASE, "[UNSUPPORTED] Unable to remove CIDR-based permission from ${description.name} (${it.toString()})."
        return false
      }

      if (it.userIdGroupPairs.find { it.userId != securityGroup.ownerId }) {
        task.updateStatus BASE_PHASE, "[UNSUPPORTED] Unable to cross account security group permission from ${description.name} (${it.toString()})."
        return false
      }

      return true
    }
  }
}
