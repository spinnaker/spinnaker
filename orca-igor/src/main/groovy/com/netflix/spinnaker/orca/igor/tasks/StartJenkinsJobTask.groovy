/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.igor.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class StartJenkinsJobTask implements RetryableTask {
  long backoffPeriod = 10000
  long timeout = 1800000

  @Autowired
  IgorService igorService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    String master = stage.context.master
    String job = stage.context.job

    Map context = objectMapper.convertValue(stage.context, Map)

    if(stage.execution instanceof Pipeline) {
      context.trigger = ((Pipeline) stage.execution).trigger
    }

    Map<String,String> parameters = stage.context.parameters
    Map parsedParameters = ContextParameterProcessor.process(parameters, context)

    Map<String, Object> build = igorService.build(master, job, parsedParameters)
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [buildNumber: build.number, parameters: parsedParameters])
  }
}
