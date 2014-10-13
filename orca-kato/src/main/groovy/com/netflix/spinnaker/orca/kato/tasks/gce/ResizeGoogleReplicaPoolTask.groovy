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

import com.netflix.spinnaker.orca.*
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.ops.gce.ResizeGoogleReplicaPoolOperation
import org.springframework.beans.factory.annotation.Autowired

class ResizeGoogleReplicaPoolTask implements Task {
  @Autowired
  KatoService kato

  @Override
  TaskResult execute(TaskContext context) {
    def resizeGoogleReplicaPoolOperation = convert(context)
    def taskId = kato.requestOperations([[resizeGoogleReplicaPoolDescription: resizeGoogleReplicaPoolOperation]])
                     .toBlocking()
                     .first()
    new DefaultTaskResult(Status.SUCCEEDED, [
      "notification.type": "resizegooglereplicapool",
                           "deploy.account.name" : resizeGoogleReplicaPoolOperation.credentials,
                           "kato.last.task.id"   : taskId,
                           "kato.task.id"        : taskId, // TODO retire this.
                           "deploy.server.groups": [(resizeGoogleReplicaPoolOperation.zone): [resizeGoogleReplicaPoolOperation.replicaPoolName]],
                          ])
  }

  ResizeGoogleReplicaPoolOperation convert(TaskContext context) {
    def inputs = context.getInputs("resizeAsg_gce")
    new ResizeGoogleReplicaPoolOperation(replicaPoolName: inputs.asgName,
                                         zone: inputs.zones ? inputs.zones[0] : null,
                                         credentials: inputs.credentials,
                                         numReplicas: inputs.capacity.desired)
  }
}
