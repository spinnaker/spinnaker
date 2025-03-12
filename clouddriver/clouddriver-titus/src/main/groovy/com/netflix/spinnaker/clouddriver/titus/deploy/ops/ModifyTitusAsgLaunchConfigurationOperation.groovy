/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.titus.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.titus.deploy.description.ModifyTitusAsgLaunchConfigurationDescription

class ModifyTitusAsgLaunchConfigurationOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "MODIFY_ASG_LAUNCH_CONFIGURATION"

  private final ModifyTitusAsgLaunchConfigurationDescription description

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  ModifyTitusAsgLaunchConfigurationOperation(ModifyTitusAsgLaunchConfigurationDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Modify Asg Launch Configuration is a Noop in Titus"
    null
  }

}
