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
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupIngressConverter.ConvertedIngress
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class UpsertSecurityGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_SG"

  /**
   * An arbitrary limit on the number of rules we will enumerate in the operation "status" logs.
   *
   * If the number of rules is greater than this number, we should just render the number of changes, rather than
   * each of the differences.
   */
  private static final int MAX_RULES_FOR_STATUS = 50

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

    // Get all of the ingress rules from the description. Will fail the operation if any upstream security groups
    // are missing, unless they're self-referential to the security group being upserted. In the case of a
    // self-referential security group, we don't want to fail the upsert if the security group doesn't exist, as we'll
    // be creating it next.
    ConvertedIngress ipPermissionsFromDescription = convertDescriptionToIngress(securityGroupLookup, description, true)

    def securityGroupUpdater = securityGroupLookup.getSecurityGroupByName(
      description.account,
      description.name,
      description.vpcId
    )

    // Create or update the security group itself. If the security group exists, also get the security group rules.
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
            description.account,
            description.name,
            description.vpcId
          ).get()
          existingIpPermissions = SecurityGroupIngressConverter.
            flattenPermissions(securityGroupUpdater.securityGroup)
        } else {
          task.updateStatus BASE_PHASE, "Failed to create security group '${description.name}' in ${description.account}: ${e.errorMessage}"
          throw e
        }
      }
    }

    // Second conversion of desired security group rules. If any upstream groups (including self-referential) are
    // missing, the operation will fail.
    if (!ipPermissionsFromDescription.missingSecurityGroups.selfReferencing.isEmpty()) {
      ipPermissionsFromDescription = convertDescriptionToIngress(securityGroupLookup, description, false)
    }

    SecurityGroupIngressConverter.IpRuleDelta ipRuleDelta = SecurityGroupIngressConverter.computeIpRuleDelta(ipPermissionsFromDescription.converted, existingIpPermissions)

    List<IpPermission> ipPermissionsToAdd = ipRuleDelta.toAdd
    List<IpPermission> userIdGroupPermissions = SecurityGroupIngressConverter.userIdGroupPairsDiff(ipPermissionsFromDescription.converted,existingIpPermissions)
    ipPermissionsToAdd = ipPermissionsToAdd + userIdGroupPermissions

    List<IpPermission> ipPermissionsToRemove = ipRuleDelta.toRemove
    List<IpPermission> userIdGroupPermissionsToRemove = SecurityGroupIngressConverter.userIdGroupPairsDiff(existingIpPermissions,ipPermissionsFromDescription.converted)
    ipPermissionsToRemove = ipPermissionsToRemove + userIdGroupPermissionsToRemove

    List<IpPermission> tobeUpdated = ipRuleDelta.toUpdate

    //Update rules that are already present on the security group
    if(tobeUpdated) {
      String status = "Permissions updated to '${description.name}'"
      if (tobeUpdated.size() > MAX_RULES_FOR_STATUS) {
        status = "$status (${tobeUpdated.size()} rules updated)."
      } else {
        status = "$status ($tobeUpdated)."
      }
      try {
        securityGroupUpdater.updateIngress(tobeUpdated)
        //Update tags to ensure they are consistent with rule changes
        securityGroupUpdater.updateTags(description)
        task.updateStatus BASE_PHASE, status
      } catch (AmazonServiceException e) {
        task.updateStatus BASE_PHASE, "Error updating ingress to '${description.name}' - ${e.errorMessage}"
        throw e
      }
    }

    // Converge on the desired final set of security group rules
    if (ipPermissionsToAdd) {
      String status = "Permissions added to '${description.name}'"
      if (ipPermissionsToAdd.size() > MAX_RULES_FOR_STATUS) {
        status = "$status (${ipPermissionsToAdd.size()} rules added)."
      } else {
        status = "$status ($ipPermissionsToAdd)."
      }

      try {
        securityGroupUpdater.addIngress(ipPermissionsToAdd)
        //Update tags to ensure they are consistent with rule changes
        securityGroupUpdater.updateTags(description)
        task.updateStatus BASE_PHASE, status
      } catch (AmazonServiceException e) {
        task.updateStatus BASE_PHASE, "Error adding ingress to '${description.name}' - ${e.errorMessage}"
        throw e
      }
    }

    if (ipPermissionsToRemove && !description.ingressAppendOnly) {
      String status = "Permissions removed from '${description.name}'"
      if (ipPermissionsToRemove.size() > MAX_RULES_FOR_STATUS) {
        status = "$status (${ipPermissionsToRemove.size()} rules removed)."
      } else {
        status = "$status ($ipPermissionsToRemove)."
      }

      try {
        securityGroupUpdater.removeIngress(ipPermissionsToRemove)
        //Update tags to ensure they are consistent with rule changes
        securityGroupUpdater.updateTags(description)
        task.updateStatus BASE_PHASE, status
      } catch (AmazonServiceException e) {
        task.updateStatus BASE_PHASE, "Error removing ingress from ${description.name}: ${e.errorMessage}"
        throw e
      }
    }
    null
  }

  private ConvertedIngress convertDescriptionToIngress(SecurityGroupLookup securityGroupLookup, UpsertSecurityGroupDescription description, boolean ignoreSelfReferencingRules) {
    ConvertedIngress ipPermissionsFromDescription = SecurityGroupIngressConverter.
      convertIngressToIpPermissions(securityGroupLookup, description)

    if (ipPermissionsFromDescription.missingSecurityGroups.anyMissing(ignoreSelfReferencingRules)) {
      def missingSecurityGroupDescriptions = ipPermissionsFromDescription.missingSecurityGroups.all.collect {
        "'${it.name ?: it.id}' in '${it.accountName ?: description.account}' ${it.vpcId ?: description.vpcId ?: 'EC2-classic'}"
      }
      def securityGroupsDoNotExistErrorMessage = "The following security groups do not exist: ${missingSecurityGroupDescriptions.join(", ")}"
      task.updateStatus BASE_PHASE, securityGroupsDoNotExistErrorMessage
      throw new IllegalStateException(securityGroupsDoNotExistErrorMessage)
    }

    return ipPermissionsFromDescription
  }
}
