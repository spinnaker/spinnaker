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
import com.amazonaws.services.ec2.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
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
                    if (e.errorCode != "InvalidGroup.NotFound") {
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
