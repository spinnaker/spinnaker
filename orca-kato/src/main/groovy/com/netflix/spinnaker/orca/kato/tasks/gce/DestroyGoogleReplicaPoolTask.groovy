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
import com.netflix.spinnaker.orca.kato.api.ops.gce.DestroyGoogleReplicaPoolOperation
import org.springframework.beans.factory.annotation.Autowired

class DestroyGoogleReplicaPoolTask implements Task {
  @Autowired
  KatoService kato

  @Override
  TaskResult execute(TaskContext context) {
    def operation = convert(context)
    def taskId = kato.requestOperations([[deleteGoogleReplicaPoolDescription: operation]])
                     .toBlocking()
                     .first()

    new DefaultTaskResult(TaskResult.Status.SUCCEEDED,
        ["deploy.account.name" : operation.credentials,
         "kato.last.task.id"   : taskId,
         "kato.task.id"        : taskId, // TODO retire this.
         "deploy.server.groups": [(operation.zone): [operation.replicaPoolName]],
        ])
  }

  DestroyGoogleReplicaPoolOperation convert(TaskContext context) {
    def inputs = context.getInputs("destroyAsg_gce")
    new DestroyGoogleReplicaPoolOperation(replicaPoolName: inputs.asgName,
                                          zone: inputs.zones ? inputs.zones[0] : null,
                                          credentials: inputs.credentials)
  }
}
