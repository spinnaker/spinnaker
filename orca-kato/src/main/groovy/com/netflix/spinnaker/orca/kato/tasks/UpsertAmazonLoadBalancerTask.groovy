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

package com.netflix.spinnaker.orca.kato.tasks

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
class UpsertAmazonLoadBalancerTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    def taskId = kato.requestOperations([[upsertAmazonLoadBalancerDescription: stage.context]])
                     .toBlocking()
                     .first()

    Map outputs = [
        "notification.type": "upsertamazonloadbalancer",
        "kato.last.task.id": taskId,
        "kato.task.id"     : taskId, // TODO retire this.
        "upsert.account"   : stage.context.credentials,
        "upsert.regions"   : ((Map) stage.context.availabilityZones).keySet().join(',')
    ]

    if (stage.context.clusterName) {
      outputs.clusterName = stage.context.clusterName
    }
    if (stage.context.name) {
      outputs.name = stage.context.name
    }

    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, outputs)
  }
}
