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
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired

import javax.annotation.Nonnull

@CompileStatic
abstract class AbstractAsgTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  abstract String getAsgAction()

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    def operation = convert(stage)
    def taskId = kato.requestOperations([[("${asgAction}Description".toString()): operation]])
                     .toBlocking()
                     .first()
    TaskResult.builder(ExecutionStatus.SUCCEEDED).context([
      "notification.type"                             : getAsgAction().toLowerCase(),
      "kato.last.task.id"                             : taskId,
      "deploy.account.name"                           : operation.credentials,
      "asgName"                                       : operation.asgName,
      ("targetop.asg.${asgAction}.name".toString())   : operation.asgName,
      ("targetop.asg.${asgAction}.regions".toString()): operation.regions,
      "deploy.server.groups"                          : (operation.regions as Collection<String>).collectEntries {
        [(it): [operation.asgName]]
      }
    ]).build()
  }

  Map convert(StageExecution stage) {
    def operation = new HashMap(stage.context)
    if (targetReferenceSupport.isDynamicallyBound(stage)) {
      def targetReference = targetReferenceSupport.getDynamicallyBoundTargetAsgReference(stage)
      operation.asgName = targetReference.asg.name
    }
    operation
  }
}
