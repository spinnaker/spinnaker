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

import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.CreateLaunchConfigDescription
import com.netflix.spinnaker.kato.deploy.aws.handlers.BasicAmazonDeployHandler
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class CreateLaunchConfigAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "CREATE_LAUNCH_CONFIG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  BasicAmazonDeployHandler basicAmazonDeployHandler

  @Autowired
  AmazonClientProvider amazonClientProvider


  final CreateLaunchConfigDescription description

  CreateLaunchConfigAtomicOperation(CreateLaunchConfigDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Create Launch Config Operation..."
    for (String region : description.regions) {
      def launchConfigOptions = description.launchConfigOptions
      // TODO: Anything special with block device mappings like Asgard had for m3 instances?
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)
      autoScaling.createLaunchConfiguration(launchConfigOptions.createLaunchConfigurationRequest)
    }
    null
  }

}