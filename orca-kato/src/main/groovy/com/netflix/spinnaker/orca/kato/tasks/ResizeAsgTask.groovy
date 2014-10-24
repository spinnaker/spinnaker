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
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.ops.ResizeAsgOperation
import com.netflix.spinnaker.orca.pipeline.Stage
import org.springframework.beans.factory.annotation.Autowired
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES

class ResizeAsgTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    def resizeAsgOperation = convert(stage)
    def taskId = kato.requestOperations([[resizeAsgDescription: resizeAsgOperation]])
                     .toBlocking()
                     .first()
    new DefaultTaskResult(PipelineStatus.SUCCEEDED, [
      "notification.type"   : "resizeasg",
      "deploy.account.name" : resizeAsgOperation.credentials,
      "kato.last.task.id"   : taskId,
      "kato.task.id"        : taskId, // TODO retire this.
      "deploy.server.groups": [(resizeAsgOperation.regions): [resizeAsgOperation.asgName]]
    ])
  }

  ResizeAsgOperation convert(Stage stage) {
    def input = stage.context
    if (stage.context.containsKey("resizeAsg")) {
      input = stage.context.resizeAsg
    }
    mapper.copy()
          .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
          .convertValue(input, ResizeAsgOperation)
  }
}
