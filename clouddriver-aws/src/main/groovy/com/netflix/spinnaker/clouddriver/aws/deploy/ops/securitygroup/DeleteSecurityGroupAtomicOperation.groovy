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
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.IpRange
import com.amazonaws.services.ec2.model.Ipv6Range
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteSecurityGroupDescription
import org.springframework.beans.factory.annotation.Autowired

class DeleteSecurityGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_SECURITY_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  private final DeleteSecurityGroupDescription description

  DeleteSecurityGroupAtomicOperation(DeleteSecurityGroupDescription description) {
    this.description = description
  }

  private void generateDependencyError(AmazonServiceException e, Task task, Map<SecurityGroup, List<IpPermission>> securityGroupToRevokeIngressPermissions) {
    List<String> dependentSecurityGroupsWithIngress = securityGroupToRevokeIngressPermissions.collect { it.key.groupName }
    String message = "Failed deleting security group because of existing dependencies. "
    if (dependentSecurityGroupsWithIngress.size() > 0) {
      message += "Ingress rules still exist on security group(s): ${dependentSecurityGroupsWithIngress.join(", ")}."
    } else {
      message += "Unknown dependencies; instances and/or load balancers may still have the security group associated."
    }

    task.updateStatus BASE_PHASE, message
    throw new Exception(message, e)
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Delete Security Group Operation..."
    for (region in description.regions) {
      def ec2 = amazonClientProvider.getAmazonEC2(description.credentials, region, true)
      def result = ec2.describeSecurityGroups()
      List<SecurityGroup> securityGroups = result.securityGroups
      SecurityGroup securityGroup = securityGroups.find { it.vpcId == description.vpcId && it.groupName == description.securityGroupName }
      String vpcText = description.vpcId ? "${description.vpcId} ": ''
      String securityGroupDescription = "${description.securityGroupName} in ${region} ${vpcText}for ${description.credentials.name}"

      if (securityGroup) {
        DeleteSecurityGroupRequest request = new DeleteSecurityGroupRequest(groupId: securityGroup.groupId)
        task.updateStatus BASE_PHASE, "Deleting ${securityGroupDescription}."
        try {
          ec2.deleteSecurityGroup(request)
        } catch (AmazonServiceException e) {
          if (e.errorCode == "DependencyViolation") {
            // Get the list of dependent ingress rules
            Map<SecurityGroup, List<IpPermission>> securityGroupToRevokeIngressPermissions = new HashMap<>()
            securityGroups.each { sg ->
              sg.ipPermissions.each { ipPerm ->
                if (ipPerm.userIdGroupPairs != null) {
                  // Check if the there is an ingress rule for the to-be-deleted security group
                  UserIdGroupPair pair = ipPerm.userIdGroupPairs.find {
                    it.groupId == securityGroup.groupId
                  }
                  if (pair != null) {
                    List<IpPermission> ipPermissions = securityGroupToRevokeIngressPermissions.get(sg)
                    // Make sure there is an index in the map
                    if (ipPermissions == null) {
                      ipPermissions = new ArrayList<>()
                      securityGroupToRevokeIngressPermissions.put(sg, ipPermissions)
                    }
                    IpPermission permission = ipPerm.clone()

                    // Make sure we only delete the security group rules and not the IP range rules
                    permission.setIpRanges(new ArrayList<String>())
                    permission.setIpv4Ranges(new ArrayList<IpRange>())
                    permission.setIpv6Ranges(new ArrayList<Ipv6Range>())
                    permission.userIdGroupPairs = [pair]

                    ipPermissions.push(permission)
                  }
                }
              }
            }
            this.generateDependencyError(e, task, securityGroupToRevokeIngressPermissions)
          } else if (e.errorCode != "InvalidGroup.NotFound") {
            task.updateStatus BASE_PHASE, e.errorMessage
            throw e
          }
        }
        task.updateStatus BASE_PHASE, "Done deleting ${securityGroupDescription}."
      } else {
        task.updateStatus BASE_PHASE, "There is no ${securityGroupDescription}."
      }
    }
    null
  }
}
