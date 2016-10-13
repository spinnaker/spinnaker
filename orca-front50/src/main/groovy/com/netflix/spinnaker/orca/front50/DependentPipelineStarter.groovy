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

package com.netflix.spinnaker.orca.front50

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.PipelineLauncher
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import groovy.util.logging.Slf4j
import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
@Slf4j
class DependentPipelineStarter implements ApplicationContextAware {
  private ApplicationContext applicationContext

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  PipelineStarter pipelineStarter

  Pipeline trigger(Map pipelineConfig, String user, Execution parentPipeline, Map suppliedParameters, String parentPipelineStageId) {
    def json = objectMapper.writeValueAsString(pipelineConfig)
    log.info('triggering dependent pipeline {}:{}', pipelineConfig.id, json)

    pipelineConfig.trigger = [
      type                     : "pipeline",
      user                     : user ?: '[anonymous]',
      parentPipelineId         : parentPipeline.id,
      parentPipelineApplication: parentPipeline.application,
      parentStatus             : parentPipeline.status,
      parentExecution          : parentPipeline,
      isPipeline               : false
    ]

    if (parentPipelineStageId) {
      pipelineConfig.trigger.parentPipelineStageId = parentPipelineStageId
    }

    if( parentPipeline instanceof Pipeline){
      pipelineConfig.trigger.parentPipelineName = parentPipeline.name
      pipelineConfig.trigger.isPipeline = true
    }

    if (pipelineConfig.parameterConfig || !suppliedParameters.empty) {
      if (!pipelineConfig.trigger.parameters) {
        pipelineConfig.trigger.parameters = [:]
      }
      def pipelineParameters = suppliedParameters ?: [:]
      pipelineConfig.parameterConfig.each {
        pipelineConfig.trigger.parameters[it.name] = pipelineParameters.containsKey(it.name) ? pipelineParameters[it.name] : it.default
      }
      suppliedParameters.each{ k, v ->
        pipelineConfig.trigger.parameters[k] = pipelineConfig.trigger.parameters[k] ?: suppliedParameters[k]
      }
    }

    def augmentedContext = [trigger: pipelineConfig.trigger]
    def processedPipeline = ContextParameterProcessor.process(pipelineConfig, augmentedContext, false)

    json = objectMapper.writeValueAsString(processedPipeline)

    log.info('running pipeline {}:{}', pipelineConfig.id, json)

    def pipeline

    def t1 = new Thread(new Runnable() {
      @Override
      public void run() {
        if (parentPipeline.executionEngine == Execution.V2_EXECUTION_ENGINE) {
          pipeline = pipelineLauncher().start(json)
        } else {
          pipeline = pipelineStarter.start(json)
        }
      }
    })

    t1.start()

    try {
      t1.join()
    } catch (InterruptedException e) {
      e.printStackTrace()
    }

    log.info('executing dependent pipeline {}', pipeline.id)

    pipeline

  }

  /**
   * There is a circular dependency between DependentPipelineStarter <-> DependentPipelineExecutionListener <->
   * SpringBatchExecutionListener that prevents a PipelineLauncher from being @Autowired.
   */
  PipelineLauncher pipelineLauncher() {
    return applicationContext.getBean(PipelineLauncher)
  }

  @Override
  void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext
  }
}
