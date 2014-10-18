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
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.ops.gce.ResizeGoogleReplicaPoolOperation
import com.netflix.spinnaker.orca.pipeline.Stage

class PreconfigureDestroyGoogleReplicaPoolTask implements Task {
  @Override
  TaskResult execute(Stage stage) {
    def op = convert(stage)
    new DefaultTaskResult(PipelineStatus.SUCCEEDED, [
      "resizeAsg_gce.credentials": op.credentials,
      "resizeAsg_gce.zones"      : [op.zone],
      "resizeAsg_gce.asgName"    : op.replicaPoolName,
      "resizeAsg_gce.capacity"   : [desired: op.numReplicas]
    ])
  }

  ResizeGoogleReplicaPoolOperation convert(Stage stage) {
    new ResizeGoogleReplicaPoolOperation(
      replicaPoolName: stage.context.asgName,
      zone: stage.context.zones ? stage.context.zones[0] : null,
      credentials: stage.context.credentials,
      numReplicas: 0
    )
  }
}
