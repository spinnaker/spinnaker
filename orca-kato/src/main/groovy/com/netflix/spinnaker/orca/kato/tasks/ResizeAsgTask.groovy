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

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.pipeline.ResizeAsgStage
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ResizeAsgTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  @Autowired
  ResizeAsgStage resizeAsgStage

  @Override
  TaskResult execute(Stage stage) {
    def operation = convert(stage)
    def taskId = kato.requestOperations([[resizeAsgDescription: operation]])
                     .toBlocking()
                     .first()
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
        "notification.type"   : "resizeasg",
        "deploy.account.name" : operation.credentials,
        "kato.last.task.id"   : taskId,
        "asgName"             : operation.asgName,
        "capacity"            : operation.capacity,
        "deploy.server.groups": operation.regions.collectEntries {
          [(it): [operation.asgName]]
        }

    ])
  }

  Map convert(Stage stage) {
    Map context = stage.context
    if (targetReferenceSupport.isDynamicallyBound(stage)) {
      def targetReference = targetReferenceSupport.getDynamicallyBoundTargetAsgReference(stage)
      def descriptors = resizeAsgStage.createResizeStageDescriptors(stage, [targetReference])
      if (!descriptors.isEmpty()) {
        context = descriptors[0]
      }
    }
    if (context.containsKey(ResizeAsgStage.PIPELINE_CONFIG_TYPE)) {
      context = (Map) context[ResizeAsgStage.PIPELINE_CONFIG_TYPE]
    }
    context
  }
}
