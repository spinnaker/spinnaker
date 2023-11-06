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
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import com.netflix.spinnaker.kork.exceptions.ConfigurationException
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.exceptions.PipelineTemplateValidationException
import com.netflix.spinnaker.orca.api.pipeline.ExecutionPreprocessor
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.util.logging.Slf4j
import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

import java.util.concurrent.Callable

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE

@Component
@Slf4j
class DependentPipelineStarter implements ApplicationContextAware {
  private ApplicationContext applicationContext
  ObjectMapper objectMapper
  ContextParameterProcessor contextParameterProcessor
  List<ExecutionPreprocessor> executionPreprocessors
  ArtifactUtils artifactUtils
  Registry registry

  @Autowired
  DependentPipelineStarter(ApplicationContext applicationContext,
                           ObjectMapper objectMapper,
                           ContextParameterProcessor contextParameterProcessor,
                           Optional<List<ExecutionPreprocessor>> executionPreprocessors,
                           Optional<ArtifactUtils> artifactUtils,
                           Registry registry) {
    this.applicationContext = applicationContext
    this.objectMapper = objectMapper
    this.contextParameterProcessor = contextParameterProcessor
    this.executionPreprocessors = executionPreprocessors.orElse(new ArrayList<>())
    this.artifactUtils = artifactUtils.orElse(null)
    this.registry = registry
  }

  PipelineExecution trigger(Map pipelineConfig,
                            String user,
                            PipelineExecution parentPipeline,
                            Map suppliedParameters,
                            String parentPipelineStageId,
                            PipelineExecution.AuthenticationDetails authenticationDetails) {
    if (pipelineConfig.disabled) {
      throw new ConfigurationException("Pipeline '${pipelineConfig.name}' is disabled and cannot be triggered")
    }

    if (log.isInfoEnabled()) {
      log.info('triggering dependent pipeline {}:{}', pipelineConfig.id,
          objectMapper.writeValueAsString(pipelineConfig))
    }

    def expectedArtifactIds = pipelineConfig.get("triggers", []).findAll {
      it.type == "pipeline" && it.pipeline == parentPipeline.pipelineConfigId
    } collectMany {
      it.expectedArtifactIds ?: []
    }

    pipelineConfig.trigger = [
      type                 : "pipeline",
      user                 : authenticationDetails?.user ?: user ?: "[anonymous]",
      parentExecution      : parentPipeline,
      parentPipelineStageId: parentPipelineStageId,
      parameters           : [:],
      strategy             : suppliedParameters.strategy == true,
      correlationId        : "${parentPipeline.id}_${parentPipelineStageId}_${pipelineConfig.id}_${parentPipeline.startTime}".toString(),
    ]
    /* correlationId is added so that two pipelines aren't triggered when a pipeline is canceled.
     * parentPipelineStageId is added so that a child pipeline (via pipeline stage)
     *  is differentiated from a downstream pipeline (via pipeline trigger).
     * pipelineConfig.id is added so that we allow two different pipelines to be triggered off the same parent.
     * parentPipeline.startTime is added so that a restarted pipeline will trigger a new round of dependent pipelines.
     */

    if (pipelineConfig.parameterConfig || !suppliedParameters.isEmpty()) {
      def pipelineParameters = suppliedParameters ?: [:]
      pipelineConfig.parameterConfig.each {
        pipelineConfig.trigger.parameters[it.name] = pipelineParameters.containsKey(it.name) ? pipelineParameters[it.name] : it.default
      }
      suppliedParameters.each { k, v ->
        pipelineConfig.trigger.parameters[k] = pipelineConfig.trigger.parameters[k] ?: suppliedParameters[k]
      }
    }

    // keep the trigger as the preprocessor may remove it. Preprocessor will
    // only remove the trigger key if the pipeline is a pipeline template
    // otherwise, the trigger remains intact
    def trigger = pipelineConfig.trigger

    if (parentPipelineStageId != null) {
      pipelineConfig.receivedArtifacts = artifactUtils?.getArtifacts(parentPipeline.stageById(parentPipelineStageId))
    } else {
      pipelineConfig.receivedArtifacts = artifactUtils?.getAllArtifacts(parentPipeline)
    }

    // This is required for template source with jinja expressions
    trigger.artifacts = pipelineConfig.receivedArtifacts

    // expectedArtifacts for triggers are defined at the top level of the
    // pipeline, so we need to extract that and match them against the incoming
    // artifacts to understand which expected artifact IDs are necessary to pass
    // to the artifact resolution
    expectedArtifactIds.addAll(pipelineConfig.get("expectedArtifacts", []).collectMany {
      def expectedArtifact = objectMapper.convertValue(it, ExpectedArtifact)
      return trigger.artifacts.findAll {
        // The idea here is we are only interested in matching against incoming
        // artifacts. So if the expected artifact does not match on any incoming
        // artifact, we will simply not add the expected artifact ID to the
        // array. This is very similar to how echo works.
        def artifact = objectMapper.convertValue(it, Artifact)
        return expectedArtifact.matches(artifact)
      }.collect {
        return expectedArtifact.id
      }
    } as String[])
    trigger.expectedArtifactIds = expectedArtifactIds

    for (ExecutionPreprocessor preprocessor : executionPreprocessors.findAll {
      it.supports(pipelineConfig, ExecutionPreprocessor.Type.PIPELINE)
    }) {
      pipelineConfig = preprocessor.process(pipelineConfig)
    }

    pipelineConfig.trigger = trigger

    def artifactError = null

    try {
      artifactUtils?.resolveArtifacts(pipelineConfig)
    } catch (Exception e) {
      artifactError = e
    }

    if (pipelineConfig.errors != null) {
      throw new PipelineTemplateValidationException("Pipeline template is invalid", pipelineConfig.errors as List<Map<String, Object>>)
    }

    // Process the raw trigger to resolve any expressions before converting it to a Trigger object, which will not be
    // processed by the contextParameterProcessor (it only handles Maps, Lists, and Strings)
    Map processedTrigger = contextParameterProcessor.process(
        [trigger: pipelineConfig.trigger],
        [trigger: pipelineConfig.trigger],
        false).trigger
    pipelineConfig.trigger = objectMapper.convertValue(processedTrigger, Trigger.class)
    if (parentPipeline.trigger.dryRun) {
      pipelineConfig.trigger.dryRun = true
    }

    def augmentedContext = [trigger: pipelineConfig.trigger]
    def processedPipeline = contextParameterProcessor.processPipeline(pipelineConfig, augmentedContext, false)

    if (log.isInfoEnabled()) {
      log.info('running pipeline {}:{}', pipelineConfig.id, objectMapper.writeValueAsString(processedPipeline))
    }

    Callable<PipelineExecution> callable
    if (artifactError == null) {
      callable = {
        return executionLauncher().start(PIPELINE, processedPipeline).with {
          Id id = registry.createId("pipelines.triggered")
              .withTag("application", Optional.ofNullable(it.getApplication()).orElse("null"))
              .withTag("monitor", "DependentPipelineStarter")
          registry.counter(id).increment()
          return it
        }
      } as Callable<PipelineExecution>
    } else {
      callable = {
        return executionLauncher().fail(PIPELINE, processedPipeline, artifactError)
      } as Callable<PipelineExecution>
    }

    def pipeline = authenticationDetails?.user ?
        AuthenticatedRequest.runAs(authenticationDetails.user, authenticationDetails.allowedAccounts, callable).call() :
        AuthenticatedRequest.propagate(callable).call()

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
