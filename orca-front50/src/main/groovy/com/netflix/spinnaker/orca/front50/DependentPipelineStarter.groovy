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
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.security.AuthenticatedRequest
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

import java.util.concurrent.Callable

@Component
@Slf4j
class DependentPipelineStarter implements ApplicationContextAware {
  private ApplicationContext applicationContext

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ContextParameterProcessor contextParameterProcessor

  Pipeline trigger(Map pipelineConfig, String user, Execution parentPipeline, Map suppliedParameters, String parentPipelineStageId) {
    def json = objectMapper.writeValueAsString(pipelineConfig)
    log.info('triggering dependent pipeline {}:{}', pipelineConfig.id, json)

    User principal = getUser(parentPipeline)

    pipelineConfig.trigger = [
      type                     : "pipeline",
      user                     : principal?.username ?: user ?: '[anonymous]',
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
    def processedPipeline = contextParameterProcessor.process(pipelineConfig, augmentedContext, false)

    json = objectMapper.writeValueAsString(processedPipeline)

    log.info('running pipeline {}:{}', pipelineConfig.id, json)

    def pipeline

    log.debug("Source thread: MDC user: " + AuthenticatedRequest.getAuthenticationHeaders() +
                  ", principal: " + principal?.toString())
    def callable = AuthenticatedRequest.propagate({
      log.debug("Destination thread user: " + AuthenticatedRequest.getAuthenticationHeaders())
      pipeline = pipelineLauncher().start(json)
    } as Callable<Void>, true, principal)

    //This needs to run in a separate thread to not bork the batch TransactionManager
    //TODO(rfletcher) - should be safe to kill this off once nu-orca merges down
    def t1 = Thread.start {
      callable.call()
    }

    try {
      t1.join()
    } catch (InterruptedException e) {
      e.printStackTrace()
    }

    log.info('executing dependent pipeline {}', pipeline.id)
    return pipeline
  }

  // There are currently two sources-of-truth for the user:
  // 1. The MDC context, which are the values that get propagated to downstream services like Front50.
  // 2. The Execution.AuthenticationDetails object.
  //
  // In the case of the implicit pipeline invocation, the MDC is empty, which is why we fall back
  // to Execution.AuthenticationDetails of the parent pipeline.
  User getUser(Execution parentPipeline) {
    def korkUsername = AuthenticatedRequest.getSpinnakerUser()
    if (korkUsername.isPresent()) {
      def korkAccounts = AuthenticatedRequest.getSpinnakerAccounts().orElse("")
      return new User(email: korkUsername.get(), allowedAccounts: korkAccounts?.split(",")?.toList() ?: []).asImmutable()
    }

    if (parentPipeline.authentication.user) {
      return parentPipeline.authentication.toKorkUser().get()
    }

    return null
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
