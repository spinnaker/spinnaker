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
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.orca.extensionpoint.pipeline.ExecutionPreprocessor
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

import java.util.concurrent.Callable

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

@Component
@Slf4j
class DependentPipelineStarter implements ApplicationContextAware {
  private ApplicationContext applicationContext
  ObjectMapper objectMapper
  ContextParameterProcessor contextParameterProcessor
  List<ExecutionPreprocessor> executionPreprocessors
  ArtifactResolver artifactResolver
  Registry registry

  @Autowired
  DependentPipelineStarter(ApplicationContext applicationContext,
                           ObjectMapper objectMapper,
                           ContextParameterProcessor contextParameterProcessor,
                           Optional<List<ExecutionPreprocessor>> executionPreprocessors,
                           Optional<ArtifactResolver> artifactResolver,
                           Registry registry) {
    this.applicationContext = applicationContext
    this.objectMapper = objectMapper
    this.contextParameterProcessor = contextParameterProcessor
    this.executionPreprocessors = executionPreprocessors.orElse(new ArrayList<>())
    this.artifactResolver = artifactResolver.orElse(null)
    this.registry = registry
  }

  Execution trigger(Map pipelineConfig,
                    String user,
                    Execution parentPipeline,
                    Map suppliedParameters,
                    String parentPipelineStageId,
                    User principal) {
    def json = objectMapper.writeValueAsString(pipelineConfig)

    if (pipelineConfig.disabled) {
      throw new InvalidRequestException("Pipeline '${pipelineConfig.name}' is disabled and cannot be triggered")
    }

    log.info('triggering dependent pipeline {}:{}', pipelineConfig.id, json)

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

    def trigger = pipelineConfig.trigger
    //keep the trigger as the preprocessor removes it.
    def expectedArtifacts = pipelineConfig.expectedArtifacts

    for (ExecutionPreprocessor preprocessor : executionPreprocessors.findAll {
      it.supports(pipelineConfig, ExecutionPreprocessor.Type.PIPELINE)
    }) {
      pipelineConfig = preprocessor.process(pipelineConfig)
    }

    if (parentPipelineStageId != null) {
      pipelineConfig.receivedArtifacts = artifactResolver?.getArtifacts(parentPipeline.stageById(parentPipelineStageId))
    } else {
      pipelineConfig.receivedArtifacts = artifactResolver?.getAllArtifacts(parentPipeline)
    }

    pipelineConfig.trigger = trigger
    pipelineConfig.expectedArtifacts = expectedArtifacts

    def artifactError = null
    try {
      artifactResolver?.resolveArtifacts(pipelineConfig)
    } catch (Exception e) {
      artifactError = e
    }

    // Process the raw trigger to resolve any expressions before converting it to a Trigger object, which will not be
    // processed by the contextParameterProcessor (it only handles Maps, Lists, and Strings)
    Map processedTrigger = contextParameterProcessor.process([trigger: pipelineConfig.trigger], [trigger: pipelineConfig.trigger], false).trigger
    pipelineConfig.trigger = objectMapper.readValue(objectMapper.writeValueAsString(processedTrigger), Trigger.class)
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

        Id id = registry.createId("pipelines.triggered")
          .withTag("application", Optional.ofNullable(pipeline.getApplication()).orElse("null"))
          .withTag("monitor", "DependentPipelineStarter")
        registry.counter(id).increment()

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
