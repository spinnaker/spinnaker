/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class RebootInstancesTask extends AbstractCloudProviderAwareTask implements Task {
  static final String CLOUD_OPERATION_TYPE = "rebootInstances"

  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)

    def taskId = kato.requestOperations(cloudProvider, [[(CLOUD_OPERATION_TYPE): stage.context]])
                     .toBlocking()
                     .first()

    new TaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"           : "rebootinstances",
      "reboot.account.name"         : account,
      "kato.last.task.id"           : taskId,
      interestingHealthProviderNames: HealthHelper.getInterestingHealthProviderNames(stage, ["Discovery"])
    ])
  }
}
