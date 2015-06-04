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

package com.netflix.spinnaker.orca.mayo.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.mayo.MayoService
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class StartPipelineTask implements Task {

  @Autowired
  MayoService mayoService

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  PipelineStarter pipelineStarter

  @Override
  TaskResult execute(Stage stage) {

    String application = stage.context.application
    List pipelines = mayoService.getPipelines(application)
    Map pipelineConfig = pipelines.find { it.id == stage.context.pipeline }

    def json = objectMapper.writeValueAsString(pipelineConfig)
    log.info('triggering dependant pipeline {}:{}', pipelineConfig.id, json)

    pipelineConfig.trigger = [
      type            : "pipeline",
      user            : stage.context.user ?: '[anonymous]',
      parentPipelineId: stage.execution.id
    ]

    if (pipelineConfig.parameterConfig) {
      if (!pipelineConfig.trigger.parameters) {
        pipelineConfig.trigger.parameters = [:]
      }
      def pipelineParameters = stage.context.pipelineParameters ?: [:]
      pipelineConfig.parameterConfig.each {
        pipelineConfig.trigger.parameters[it.name] = pipelineParameters.containsKey(it.name) ? pipelineParameters[it.name] : it.default
      }
    }

    def augmentedContext = [:]
    augmentedContext.put('trigger', pipelineConfig.trigger)
    def processedPipeline = ContextParameterProcessor.process(pipelineConfig, augmentedContext)

    json = objectMapper.writeValueAsString(processedPipeline)

    log.info('running pipeline {}:{}', pipelineConfig.id, json)


    def pipeline

    def t1 = new Thread( new Runnable() {
      @Override
      public void run() {
        pipeline = pipelineStarter.start(json)
      }
    })

    t1.start()

    try {
      t1.join()
    } catch (InterruptedException e) {
      e.printStackTrace()
    }

    log.info('executing dependent pipeline {}', pipeline.id)

    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [executionId: pipeline.id])

  }

}
