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
      description.credentialAccount,
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

    // Second conversion of desired security group rules. If any upstream groups (including self-referential) are
    // missing, the operation will fail.
    if (!ipPermissionsFromDescription.missingSecurityGroups.selfReferencing.isEmpty()) {
      ipPermissionsFromDescription = convertDescriptionToIngress(securityGroupLookup, description, false)
    }

    List<IpPermission> ipPermissionsToAdd = ipPermissionsFromDescription.converted - existingIpPermissions
    List<IpPermission> ipPermissionsToRemove = existingIpPermissions - ipPermissionsFromDescription.converted

    // Converge on the desired final set of security group rules
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

  private ConvertedIngress convertDescriptionToIngress(SecurityGroupLookup securityGroupLookup, UpsertSecurityGroupDescription description, boolean ignoreSelfReferencingRules) {
    ConvertedIngress ipPermissionsFromDescription = SecurityGroupIngressConverter.
      convertIngressToIpPermissions(securityGroupLookup, description)

    if (ipPermissionsFromDescription.missingSecurityGroups.anyMissing(ignoreSelfReferencingRules)) {
      def missingSecurityGroupDescriptions = ipPermissionsFromDescription.missingSecurityGroups.all.collect {
        "'${it.name ?: it.id}' in '${it.accountName ?: description.credentialAccount}' ${it.vpcId ?: description.vpcId ?: 'EC2-classic'}"
      }
      def securityGroupsDoNotExistErrorMessage = "The following security groups do not exist: ${missingSecurityGroupDescriptions.join(", ")}"
      task.updateStatus BASE_PHASE, securityGroupsDoNotExistErrorMessage
      throw new IllegalStateException(securityGroupsDoNotExistErrorMessage)
    }

    return ipPermissionsFromDescription
  }
}
