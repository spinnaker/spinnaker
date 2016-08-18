/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class UpsertSecurityGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_SG"

  final UpsertSecurityGroupDescription description

  UpsertSecurityGroupAtomicOperation(UpsertSecurityGroupDescription description) {
    this.description = description
  }

  @Autowired
  SecurityGroupLookupFactory securityGroupLookupFactory

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    final securityGroupLookup = securityGroupLookupFactory.getInstance(description.region)
    final ipPermissionsFromDescription = SecurityGroupIngressConverter.
      convertIngressToIpPermissions(securityGroupLookup, description)
    if (ipPermissionsFromDescription.missingSecurityGroups) {
      def missingSecurityGroupDescriptions = ipPermissionsFromDescription.missingSecurityGroups.collect {
        "'${it.name ?: it.id}' in '${it.accountName ?: description.credentialAccount}' ${it.vpcId ?: description.vpcId ?: 'EC2-classic'}"
      }
      def securityGroupsDoNotExistErrorMessage = "The following security groups do not exist: ${missingSecurityGroupDescriptions.join(", ")}"
      task.updateStatus BASE_PHASE, securityGroupsDoNotExistErrorMessage
      throw new IllegalStateException(securityGroupsDoNotExistErrorMessage)
    }

    def securityGroupUpdater = securityGroupLookup.getSecurityGroupByName(
      description.credentialAccount,
      description.name,
      description.vpcId
    )

    List<IpPermission> existingIpPermissions
    if (securityGroupUpdater.present) {
      securityGroupUpdater = securityGroupUpdater.get()
      existingIpPermissions = SecurityGroupIngressConverter.
        flattenPermissions(securityGroupUpdater.securityGroup)
    } else {
      try {
        securityGroupUpdater = securityGroupLookup.createSecurityGroup(description)
        task.updateStatus BASE_PHASE, "Security group created: ${securityGroupUpdater.securityGroup}."
        existingIpPermissions = []
      } catch (AmazonServiceException e) {
        if (e.errorCode == "InvalidGroup.Duplicate") {
          securityGroupUpdater = securityGroupLookup.getSecurityGroupByName(
            description.credentialAccount,
            description.name,
            description.vpcId
          ).get()
          existingIpPermissions = SecurityGroupIngressConverter.
            flattenPermissions(securityGroupUpdater.securityGroup)
        } else {
          task.updateStatus BASE_PHASE, "Failed to create security group '${description.name}' in ${description.credentialAccount}: ${e.errorMessage}"
          throw e
        }
      }
    }

    List<IpPermission> ipPermissionsToAdd = findMissing(ipPermissionsFromDescription.converted, existingIpPermissions)
    List<IpPermission> ipPermissionsToRemove = findMissing(existingIpPermissions, ipPermissionsFromDescription.converted)

    if (ipPermissionsToAdd) {
      try {
        securityGroupUpdater.addIngress(ipPermissionsToAdd)
        task.updateStatus BASE_PHASE, "Permissions added to '${description.name}' (${ipPermissionsToAdd})."
      } catch (AmazonServiceException e) {
        task.updateStatus BASE_PHASE, "Error adding ingress to '${description.name}' - ${e.errorMessage}"
        throw e
      }
    }
    if (ipPermissionsToRemove && !description.ingressAppendOnly) {
      try {
        securityGroupUpdater.removeIngress(ipPermissionsToRemove)
        task.updateStatus BASE_PHASE, "Permissions removed from ${description.name} (${ipPermissionsToRemove})."
      } catch (AmazonServiceException e) {
        task.updateStatus BASE_PHASE, "Error removing ingress from ${description.name}: ${e.errorMessage}"
        throw e
      }
    }
    null
  }

  static List<IpPermission> findMissing(List<IpPermission> src, List<IpPermission> expected) {
    src.findResults { toCheck ->
      expected.find {
        it.fromPort == toCheck.fromPort &&
          it.toPort == toCheck.toPort &&
          it.ipProtocol == toCheck.ipProtocol &&
          pairsEqual(it.userIdGroupPairs, toCheck.userIdGroupPairs) &&
          rangesEqual(it.ipRanges, toCheck.ipRanges)
      } ? null : toCheck
    }
  }

  static boolean pairsEqual(Collection<UserIdGroupPair> pairsA, Collection<UserIdGroupPair> pairsB) {
    if (pairsA) {
      if (pairsA.size() != pairsB?.size()) {
        false
      } else {
        Map matched = new IdentityHashMap()
        pairsA.every { a ->
          def match = pairsB.find { b ->
            if (matched.containsKey(b)) {
              return false
            }

            //Pairs are equal if
            // - userIds and vpcIds match
            // - both have a groupId and those match
            //   or
            //   both have a groupName and those match
            //
            // we support the case of one pair having a
            // groupId present and the other not and falling
            // back to matching on name because we may be
            // dealing with cross-account permissions from
            // an account that Spinnaker does not manage so
            // can not go resolve the groupId from the name
            if (a.userId != b.userId) {
              return false
            }
            if (a.vpcId != b.vpcId) {
              return false
            }
            if (a.groupId && b.groupId) {
              return a.groupId == b.groupId
            }
            if (a.groupName && b.groupName) {
              return a.groupName == b.groupName
            }
            return false
          }
          match && matched.put(match, Boolean.TRUE) == null
        }
        matched.keySet().size() == pairsB.size()
      }
    } else {
      !pairsB
    }
  }

  static boolean rangesEqual(Collection<String> rangesA, Collection<String> rangesB) {
    if (rangesA) {
      rangesA.sort() == rangesB?.sort()
    } else {
      !rangesB
    }
  }

}
