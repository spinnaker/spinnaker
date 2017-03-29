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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
abstract class AbstractDiscoveryTask implements Task {
  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  abstract String getAction()

  @Override
  TaskResult execute(Stage stage) {
    def taskId = kato.requestOperations([["${action}": stage.context]])
      .toBlocking()
      .first()
    new TaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"           : getAction().toLowerCase(),
      "kato.last.task.id"           : taskId,
      interestingHealthProviderNames: HealthHelper.getInterestingHealthProviderNames(stage, ["Discovery"])
    ])
  }
}
