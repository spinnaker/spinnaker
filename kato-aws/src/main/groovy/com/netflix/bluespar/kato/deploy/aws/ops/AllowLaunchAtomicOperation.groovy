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

package com.netflix.bluespar.kato.deploy.aws.ops

import com.amazonaws.services.ec2.model.LaunchPermission
import com.amazonaws.services.ec2.model.LaunchPermissionModifications
import com.amazonaws.services.ec2.model.ModifyImageAttributeRequest
import com.netflix.bluespar.amazon.security.AmazonClientProvider
import com.netflix.bluespar.kato.data.task.Task
import com.netflix.bluespar.kato.data.task.TaskRepository
import com.netflix.bluespar.kato.deploy.aws.description.AllowLaunchDescription
import com.netflix.bluespar.kato.orchestration.AtomicOperation
import com.netflix.bluespar.kato.security.NamedAccountCredentialsHolder
import com.netflix.bluespar.kato.security.aws.AmazonRoleAccountCredentials
import org.springframework.beans.factory.annotation.Autowired

class AllowLaunchAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ALLOW_LAUNCH"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final AllowLaunchDescription description

  AllowLaunchAtomicOperation(AllowLaunchDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  NamedAccountCredentialsHolder namedAccountCredentialsHolder

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Allow Launch Operation..."

    def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, description.region)

    def credentials = namedAccountCredentialsHolder.getCredentials(description.account) as AmazonRoleAccountCredentials

    task.updateStatus BASE_PHASE, "Allowing launch of $description.amiName from $description.account"
    amazonEC2.modifyImageAttribute(new ModifyImageAttributeRequest().withImageId(description.amiName).withLaunchPermission(new LaunchPermissionModifications()
      .withAdd(new LaunchPermission().withUserId(credentials.accountId))))
    task.updateStatus BASE_PHASE, "Done allowing launch of $description.amiName from $description.account."
  }
}
