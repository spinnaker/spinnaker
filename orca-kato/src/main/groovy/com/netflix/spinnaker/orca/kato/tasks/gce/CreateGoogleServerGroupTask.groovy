/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks.gce

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.kato.api.ops.gce.DeployGoogleServerGroupOperation
import com.netflix.spinnaker.orca.pipeline.Stage
import org.springframework.beans.factory.annotation.Autowired

class CreateGoogleServerGroupTask implements Task {

  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    def operation = convert(context)
    def taskId = deploy(operation)
    new DefaultTaskResult(PipelineStatus.SUCCEEDED,
        [
            "notification.type"  : "createdeploy",
            "kato.last.task.id"  : taskId,
            "kato.task.id"       : taskId, // TODO retire this.
            "deploy.account.name": operation.credentials,
        ]
    )
  }

  DeployGoogleServerGroupOperation convert(TaskContext context) {
    def inputs = context.getInputs("deploy_gce")
    new DeployGoogleServerGroupOperation(application: inputs.application,
                                         stack: inputs.stack,
                                         freeFormDetails: inputs.freeFormDetails,
                                         image: inputs.image,
                                         type: inputs.machineType,
                                         zone: inputs.zones ? inputs.zones[0] : null,
                                         initialNumReplicas: inputs.capacity.desired,
                                         credentials: inputs.credentials)
  }

  private TaskId deploy(DeployGoogleServerGroupOperation deployOperation) {
    def result = kato.requestOperations([[basicGoogleDeployDescription: deployOperation]]).toBlocking().first()
    result
  }
}
