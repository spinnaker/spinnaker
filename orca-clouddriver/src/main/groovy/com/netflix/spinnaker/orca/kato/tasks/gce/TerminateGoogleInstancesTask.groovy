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

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class TerminateGoogleInstancesTask implements Task {
  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    def katoRequest = convert(stage)
    def taskId = kato.requestOperations([katoRequest])
                     .toBlocking()
                     .first()

    // TODO(duftler): Reconcile the mismatch between region and zone here.
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
        "notification.type"     : "terminategoogleinstances",
        "terminate.account.name": stage.context.credentials,
        "terminate.region"      : stage.context.zone,
        "kato.last.task.id"     : taskId,
        "kato.task.id"          : taskId, // TODO retire this.
        "terminate.instance.ids": stage.context.instanceIds,
    ])
  }

  // If the instance is contained within a server group, we want to delegate to the kato task that results in a managed
  // instance groups recreate instances operation. If the instance is standalone, we want to delegate to the kato task
  // that results in instance delete operations.
  Map convert(Stage stage) {
    def operation = [:]

    operation.putAll(stage.context)

    if (operation.serverGroup) {
      operation.replicaPoolName = operation.remove('serverGroup')
    }

    def katoOperationDescription = operation.replicaPoolName
                                   ? 'recreateGoogleReplicaPoolInstancesDescription'
                                   : 'terminateGoogleInstancesDescription'
    def katoRequest = [:]

    katoRequest[katoOperationDescription] = operation

    katoRequest
  }
}
