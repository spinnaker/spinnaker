/*
 * Copyright 2015 Google, Inc.
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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class UpsertGoogleLoadBalancerTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    def operation = convert(stage)

    def taskId = kato.requestOperations([[createGoogleNetworkLoadBalancerDescription: operation]])
                     .toBlocking()
                     .first()

    Map outputs = [
        "notification.type": "upsertgoogleloadbalancer",
        "kato.last.task.id": taskId,
        "kato.task.id"     : taskId, // TODO retire this.
        "upsert.account"   : stage.context.credentials,
        "upsert.regions"   : [operation.region]
    ]

    if (stage.context.clusterName) {
      outputs.clusterName = stage.context.clusterName
    }

    if (stage.context.name) {
      outputs.name = stage.context.name
    }

    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, outputs)
  }

  Map convert(Stage stage) {
    def operation = [:]
    def context = stage.context
    operation.networkLoadBalancerName = context.name
    operation.region = context.region
    operation.credentials = context.credentials

    def listener = context.listeners?.get(0)

    if (listener?.protocol) {
      operation.ipProtocol = listener.protocol;
    }

    if (listener?.portRange) {
      operation.portRange = listener.portRange;
    }

    if (listener?.healthCheck) {
      operation.healthCheck = [
        port: context.healthCheckPort,
        requestPath: context.healthCheckPath,
        timeoutSec: context.healthTimeout,
        checkIntervalSec: context.healthInterval,
        healthyThreshold: context.healthyThreshold,
        unhealthyThreshold: context.unhealthyThreshold
      ]
    }

    operation
  }
}
