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

package com.netflix.spinnaker.orca.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.shared.FiatService
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.kork.web.exceptions.ValidationException
import com.netflix.spinnaker.orca.extensionpoint.pipeline.PipelinePreprocessor
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.model.Trigger
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipelinetemplate.PipelineTemplateService
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import javax.servlet.http.HttpServletResponse

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import static net.logstash.logback.argument.StructuredArguments.value

@RestController
@Slf4j
class OperationsController {
  @Autowired
  ExecutionLauncher executionLauncher

  @Autowired(required = false)
  BuildService buildService

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ExecutionRepository executionRepository

  @Autowired(required = false)
  PipelineTemplateService pipelineTemplateService

  @Autowired
  ContextParameterProcessor contextParameterProcessor

  @Autowired(required = false)
  List<PipelinePreprocessor> pipelinePreprocessors

  @Autowired(required = false)
  WebhookService webhookService

  @Autowired(required = false)
  ArtifactResolver artifactResolver

  @Autowired
  FiatStatus fiatStatus

  @Autowired(required = false)
  FiatService fiatService

  @RequestMapping(value = "/orchestrate", method = RequestMethod.POST)
  Map<String, Object> orchestrate(@RequestBody Map pipeline, HttpServletResponse response) {
    Exception pipelineError = null
    boolean plan = pipeline.plan ?: false
    try {
      pipeline = parseAndValidatePipeline(pipeline)
    } catch (Exception e) {
      pipelineError = e
    }

    if (plan) {
      log.info('Not starting pipeline (plan: true): {}', value("pipelineId", pipeline.id))
      if (pipelineError != null) {
        throw pipelineError
      }
      return pipeline
    }

    def augmentedContext = [trigger: pipeline.trigger]
    def processedPipeline = contextParameterProcessor.process(pipeline, augmentedContext, false)
    processedPipeline.trigger = objectMapper.convertValue(processedPipeline.trigger, Trigger)

    if (pipelineError == null) {
      startPipeline(processedPipeline)
    } else {
      markPipelineFailed(processedPipeline, pipelineError)
      throw pipelineError
    }
  }

  private Map parseAndValidatePipeline(Map pipeline) {
    parsePipelineTrigger(executionRepository, buildService, pipeline)

    for (PipelinePreprocessor preprocessor : (pipelinePreprocessors ?: [])) {
      pipeline = preprocessor.process(pipeline)
    }

    def json = objectMapper.writeValueAsString(pipeline)
    log.info('received pipeline {}:{}', value("pipelineId", pipeline.id), json)

    if (pipeline.disabled) {
      throw new InvalidRequestException("Pipeline is disabled and cannot be started.")
    }

    def linear = pipeline.stages.every { it.refId == null }
    if (linear) {
      convertLinearToParallel(pipeline)
    }

    if (pipeline.errors != null) {
      throw new ValidationException("Pipeline template is invalid", pipeline.errors as List<Map<String, Object>>)
    }
    return pipeline
  }

  private void parsePipelineTrigger(ExecutionRepository executionRepository, BuildService buildService, Map pipeline) {
    if (!(pipeline.trigger instanceof Map)) {
      pipeline.trigger = [:]
      if (pipeline.plan && pipeline.type == "templatedPipeline" && pipelineTemplateService != null) {
        // If possible, initialize the config with a previous execution trigger context, to be able to resolve
        // dynamic parameters in jinja expressions
        try {
          def previousExecution = pipelineTemplateService.retrievePipelineOrNewestExecution(pipeline.executionId, pipeline.id)
          pipeline.trigger = objectMapper.convertValue(previousExecution.trigger, Map)
          pipeline.executionId = previousExecution.id
        } catch (ExecutionNotFoundException | IllegalArgumentException _) {
          log.info("Could not initialize pipeline template config from previous execution context.")
        }
      }
    }

    if (!pipeline.trigger.type) {
      pipeline.trigger.type = "manual"
    }

    if (!pipeline.trigger.user) {
      pipeline.trigger.user = AuthenticatedRequest.getSpinnakerUser().orElse("[anonymous]")
    }

    if (buildService) {
      getBuildInfo(pipeline.trigger)
    }

    if (pipeline.trigger.parentPipelineId && !pipeline.trigger.parentExecution) {
      def parentExecution = executionRepository.retrieve(PIPELINE, pipeline.trigger.parentPipelineId)
      if (parentExecution) {
        pipeline.trigger.isPipeline         = true
        pipeline.trigger.parentStatus       = parentExecution.status
        pipeline.trigger.parentExecution    = parentExecution
        pipeline.trigger.parentPipelineName = parentExecution.name

        pipeline.receivedArtifacts = artifactResolver.getAllArtifacts(parentExecution)
      }
    }

    if (!pipeline.trigger.parameters) {
      pipeline.trigger.parameters = [:]
    }

    if (pipeline.parameterConfig) {
      pipeline.parameterConfig.each {
        pipeline.trigger.parameters[it.name] = pipeline.trigger.parameters.containsKey(it.name) ? pipeline.trigger.parameters[it.name] : it.default
      }
    }

    if (!pipeline.plan) {
      artifactResolver?.resolveArtifacts(pipeline)
    }
  }

  private void getBuildInfo(Map trigger) {
    if (trigger.master && trigger.job && trigger.buildNumber) {
      def buildInfo = buildService.getBuild(trigger.buildNumber, trigger.master, trigger.job)
      if (buildInfo?.artifacts) {
        if (trigger.type == "manual") {
          trigger.artifacts = buildInfo.artifacts
        }
      }
      trigger.buildInfo = buildInfo
      if (trigger.propertyFile) {
        trigger.properties = buildService.getPropertyFile(
          trigger.buildNumber as Integer,
          trigger.propertyFile as String,
          trigger.master as String,
          trigger.job as String
        )
      }
    } else if (trigger?.registry && trigger?.repository && trigger?.tag) {
      trigger.buildInfo = [
        taggedImages: [[registry: trigger.registry, repository: trigger.repository, tag: trigger.tag]]
      ]
    }
  }

  @RequestMapping(value = "/ops", method = RequestMethod.POST)
  Map<String, String> ops(@RequestBody List<Map> input) {
    def execution = [application: null, name: null, stages: input]
    parsePipelineTrigger(executionRepository, buildService, execution)
    startTask(execution)
  }

  @RequestMapping(value = "/ops", consumes = "application/context+json", method = RequestMethod.POST)
  Map<String, String> ops(@RequestBody Map input) {
    def execution = [application: input.application, name: input.description, stages: input.job, trigger: input.trigger ?: [:]]
    parsePipelineTrigger(executionRepository, buildService, execution)
    startTask(execution)
  }

  @RequestMapping(value = "/webhooks/preconfigured")
  List<Map<String, Object>> preconfiguredWebhooks() {
    if (!webhookService) {
      return []
    }
    def webhooks = webhookService.preconfiguredWebhooks

    if (fiatStatus.isEnabled()) {
      String user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
      UserPermission.View userPermission = fiatService.getUserPermission(user)

      webhooks = webhooks.findAll { it.isAllowed("READ", userPermission.roles) }
    }

    return webhooks.collect {
      [ label: it.label,
        description: it.description,
        type: it.type,
        waitForCompletion: it.waitForCompletion,
        preconfiguredProperties: it.preconfiguredProperties,
        noUserConfigurableFields: it.noUserConfigurableFields(),
        parameters: it.parameters,
      ]
    }
  }

  private void convertLinearToParallel(Map<String, Serializable> pipelineConfig) {
    def stages = (List<Map<String, Object>>) pipelineConfig.stages
    stages.eachWithIndex { Map<String, Object> stage, int index ->
      stage.put("refId", String.valueOf(index))
      if (index > 0) {
        stage.put("requisiteStageRefIds", Collections.singletonList(String.valueOf(index - 1)))
      } else {
        stage.put("requisiteStageRefIds", Collections.emptyList())
      }
    }
  }

  private Map<String, String> startPipeline(Map config) {
    injectPipelineOrigin(config)
    def json = objectMapper.writeValueAsString(config)
    log.info('requested pipeline: {}', json)

    def pipeline = executionLauncher.start(PIPELINE, json)

    [ref: "/pipelines/${pipeline.id}".toString()]
  }

  private Map<String, String> markPipelineFailed(Map config, Exception e) {
    injectPipelineOrigin(config)
    def json = objectMapper.writeValueAsString(config)
    log.warn('requested pipeline marked as failed: {}', json)

    def pipeline = executionLauncher.fail(PIPELINE, json, e)

    [ref: "/pipelines/${pipeline.id}".toString()]
  }

  private Map<String, String> startTask(Map config) {
    def linear = config.stages.every { it.refId == null }
    if (linear) {
      convertLinearToParallel(config)
    }
    injectPipelineOrigin(config)
    def json = objectMapper.writeValueAsString(config)
    log.info('requested task:{}', json)
    def pipeline = executionLauncher.start(ORCHESTRATION, json)
    [ref: "/tasks/${pipeline.id}".toString()]
  }

  private void injectPipelineOrigin(Map pipeline) {
    if (!pipeline.origin) {
      pipeline.origin = AuthenticatedRequest.spinnakerUserOrigin.orElse('unknown')
    }
  }
}
