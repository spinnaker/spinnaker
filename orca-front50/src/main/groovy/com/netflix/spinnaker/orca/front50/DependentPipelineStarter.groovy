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

import java.util.concurrent.Callable
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.orca.extensionpoint.pipeline.PipelinePreprocessor
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Trigger
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.security.AuthenticatedRequest
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

@Component
@Slf4j
class DependentPipelineStarter implements ApplicationContextAware {
  private ApplicationContext applicationContext

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ContextParameterProcessor contextParameterProcessor

  @Autowired(required = false)
  List<PipelinePreprocessor> pipelinePreprocessors

  @Autowired(required = false)
  ArtifactResolver artifactResolver

  Execution trigger(Map pipelineConfig, String user, Execution parentPipeline, Map suppliedParameters, String parentPipelineStageId) {
    def json = objectMapper.writeValueAsString(pipelineConfig)

    if (pipelineConfig.disabled) {
      throw new InvalidRequestException("Pipeline '${pipelineConfig.name}' is disabled and cannot be triggered")
    }

    log.info('triggering dependent pipeline {}:{}', pipelineConfig.id, json)

    User principal = getUser(parentPipeline)

    pipelineConfig.trigger = [
      type                 : "pipeline",
      user                 : principal?.username ?: user ?: "[anonymous]",
      parentExecution      : parentPipeline,
      parentPipelineStageId: parentPipelineStageId,
      parameters           : [:],
      strategy             : suppliedParameters.strategy == true
    ]

    if (pipelineConfig.parameterConfig || !suppliedParameters.empty) {
      def pipelineParameters = suppliedParameters ?: [:]
      pipelineConfig.parameterConfig.each {
        pipelineConfig.trigger.parameters[it.name] = pipelineParameters.containsKey(it.name) ? pipelineParameters[it.name] : it.default
      }
      suppliedParameters.each { k, v ->
        pipelineConfig.trigger.parameters[k] = pipelineConfig.trigger.parameters[k] ?: suppliedParameters[k]
      }
    }

    if (parentPipelineStageId != null) {
      pipelineConfig.receivedArtifacts = artifactResolver?.getArtifacts(parentPipeline.stageById(parentPipelineStageId))
    } else {
      pipelineConfig.receivedArtifacts = artifactResolver?.getAllArtifacts(parentPipeline)
    }

    def trigger = pipelineConfig.trigger
    //keep the trigger as the preprocessor removes it.

    for (PipelinePreprocessor preprocessor : (pipelinePreprocessors ?: [])) {
      pipelineConfig = preprocessor.process(pipelineConfig)
    }
    pipelineConfig.trigger = trigger

    def artifactError = null
    try {
      artifactResolver?.resolveArtifacts(pipelineConfig)
    } catch (Exception e) {
      artifactError = e
    }

    pipelineConfig.trigger = objectMapper.readValue(objectMapper.writeValueAsString(pipelineConfig.trigger), Trigger.class)
    if (parentPipeline.trigger.dryRun) {
      pipelineConfig.trigger.dryRun = true
    }

    def augmentedContext = [trigger: pipelineConfig.trigger]
    def processedPipeline = contextParameterProcessor.process(pipelineConfig, augmentedContext, false)

    json = objectMapper.writeValueAsString(processedPipeline)

    log.info('running pipeline {}:{}', pipelineConfig.id, json)

    log.debug("Source thread: MDC user: " + AuthenticatedRequest.getAuthenticationHeaders() +
      ", principal: " + principal?.toString())

    def pipeline
    def callable
    if (artifactError == null) {
      callable = AuthenticatedRequest.propagate({
        log.debug("Destination thread user: " + AuthenticatedRequest.getAuthenticationHeaders())
        pipeline = executionLauncher().start(PIPELINE, json)
      } as Callable<Void>, true, principal)
    } else {
      callable = AuthenticatedRequest.propagate({
        log.debug("Destination thread user: " + AuthenticatedRequest.getAuthenticationHeaders())
        pipeline = executionLauncher().fail(PIPELINE, json, artifactError)
      } as Callable<Void>, true, principal)
    }

    //This needs to run in a separate thread to not bork the batch TransactionManager
    //TODO(rfletcher) - should be safe to kill this off once nu-orca merges down
    def t1 = Thread.start {
      callable.call()
    }

    try {
      t1.join()
    } catch (InterruptedException e) {
      log.warn("Thread interrupted", e)
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
   * SpringBatchExecutionListener that prevents a ExecutionLauncher from being @Autowired.
   */
  ExecutionLauncher executionLauncher() {
    return applicationContext.getBean(ExecutionLauncher)
  }

  @Override
  void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext
  }
}
