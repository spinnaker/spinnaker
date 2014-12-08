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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.ops.gce.DisableGoogleServerGroupOperation
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES

class DisableGoogleServerGroupTask implements Task {
  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    def operation = convert(stage)
    def taskId = kato.requestOperations([[disableGoogleReplicaPoolDescription: operation]])
                     .toBlocking()
                     .first()

    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"   : "disablegoogleservergroup",
      "deploy.account.name" : operation.credentials,
      "kato.last.task.id"   : taskId,
      "kato.task.id"        : taskId, // TODO retire this.
      "deploy.server.groups": [(operation.zone): [operation.replicaPoolName]],
    ])
  }

  DisableGoogleServerGroupOperation convert(Stage stage) {
    def operation = [:]
    operation.putAll(stage.context)
    operation.replicaPoolName = operation.asgName
    operation.zone = operation.zones ? operation.zones[0] : null

    mapper.copy()
          .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
          .convertValue(operation, DisableGoogleServerGroupOperation)
  }
}
