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
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired

import javax.annotation.Nonnull

@CompileStatic
abstract class AbstractDiscoveryTask implements Task {
  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  abstract String getAction()

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    def taskId = kato.requestOperations([["${action}": stage.context]])
      .toBlocking()
      .first()
    TaskResult.builder(ExecutionStatus.SUCCEEDED).context([
      "notification.type"           : getAction().toLowerCase(),
      "kato.last.task.id"           : taskId,
      interestingHealthProviderNames: HealthHelper.getInterestingHealthProviderNames(stage, ["Discovery"])
    ]).build()
  }
}
