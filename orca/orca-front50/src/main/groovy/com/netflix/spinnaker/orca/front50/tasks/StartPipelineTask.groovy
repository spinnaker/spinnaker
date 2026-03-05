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

package com.netflix.spinnaker.orca.front50.tasks

import com.netflix.spinnaker.kork.exceptions.ConfigurationException
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.ExecutionPreprocessor
import com.netflix.spinnaker.orca.front50.DependentPipelineStarter
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipelinetemplate.V2Util
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

import static java.net.HttpURLConnection.HTTP_NOT_FOUND

@Component
@Slf4j
class StartPipelineTask implements Task {

  private final Front50Service front50Service

  private final DependentPipelineStarter dependentPipelineStarter

  private final ContextParameterProcessor contextParameterProcessor

  private final List<ExecutionPreprocessor> executionPreprocessors

  @Autowired
  StartPipelineTask(Optional<Front50Service> front50Service, DependentPipelineStarter dependentPipelineStarter, ContextParameterProcessor contextParameterProcessor, Optional<List<ExecutionPreprocessor>> executionPreprocessors) {
    this.front50Service = front50Service.orElse(null)
    this.dependentPipelineStarter = dependentPipelineStarter
    this.contextParameterProcessor = contextParameterProcessor
    this.executionPreprocessors = executionPreprocessors.orElse(Collections.emptyList())
  }

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    if (!front50Service) {
      throw new UnsupportedOperationException("Cannot start a stored pipeline, front50 is not enabled. Fix this by setting front50.enabled: true")
    }

    String application = stage.context.pipelineApplication ?: stage.context.application
    Boolean isStrategy = stage.context.pipelineParameters?.strategy ?: false
    String pipelineId = isStrategy ? stage.context.pipelineId : stage.context.pipeline

    Map<String, Object> pipelineConfig = isStrategy
      ? Retrofit2SyncCall.execute(front50Service.getStrategies(application)).find { it.id == pipelineId }
      : getPipelineById(pipelineId)

    if (!pipelineConfig) {
      throw new ConfigurationException("The referenced ${isStrategy ? 'custom strategy' : 'pipeline'} cannot be located (${pipelineId})")
    }

    if (pipelineConfig.getOrDefault("disabled", false)) {
      throw new ConfigurationException("The referenced ${isStrategy ? 'custom strategy' : 'pipeline'} is disabled")
    }

    if (V2Util.isV2Pipeline(pipelineConfig)) {
      pipelineConfig = V2Util.planPipeline(contextParameterProcessor, executionPreprocessors, pipelineConfig)
    }

    Map parameters = stage.context.pipelineParameters ?: [:]

    if (isStrategy) {
      def deploymentDetails = stage.context.deploymentDetails?.collect { Map it ->
        def base = [ami: it.ami, imageName: it.imageName, imageId: it.imageId]
        if (it.region) {
          base.region = it.region
        } else if (it.zone) {
          base.zone = it.zone
        }
        return base
      } ?: [:]

      if (deploymentDetails) {
        parameters.deploymentDetails = deploymentDetails
        if (!parameters.amiName && (parameters.region || parameters.zone)) {
          def details = deploymentDetails.find { (it.region && it.region == parameters.region) ||
                                                 (it.zone && it.zone == parameters.zone) }
          if (details) {
            parameters.amiName = details.ami
            parameters.imageId = details.imageId
          }
        }
      }
    }

    def pipeline = dependentPipelineStarter.trigger(
      pipelineConfig,
      stage.context.user as String,
      stage.execution,
      parameters,
      stage.id,
      getUser(stage.execution)
    )

    TaskResult.builder(ExecutionStatus.SUCCEEDED).context([executionId: pipeline.id, executionName: pipelineConfig.name]).build()
  }

  // There are currently two sources-of-truth for the user:
  // 1. The MDC context, which are the values that get propagated to downstream services like Front50.
  // 2. The Execution.AuthenticationDetails object.
  //
  // In the case of the implicit pipeline invocation, the MDC is empty, which is why we fall back
  // to Execution.AuthenticationDetails of the parent pipeline.
  PipelineExecution.AuthenticationDetails getUser(PipelineExecution parentPipeline) {
    def korkUsername = AuthenticatedRequest.getSpinnakerUser()
    if (korkUsername.isPresent()) {
      def korkAccounts = AuthenticatedRequest.getSpinnakerAccounts().orElse("")
      return new PipelineExecution.AuthenticationDetails(korkUsername.get(), korkAccounts.split(","))
    }

    if (parentPipeline.authentication?.user) {
      return parentPipeline.authentication
    }

    return null
  }


  /**
   * Fetches a pipeline from front50 if it exists.
   * Returns a null if the pipeline doesn't exist in front50
   * @param id id of the pipeline to be fetched from front50
   * @return fetched pipeline if it exists, null otherwise
   */
  private Map<String, Object> getPipelineById(String id) {
    try {
      return Retrofit2SyncCall.execute(front50Service.getPipeline(id))
    } catch (SpinnakerHttpException e) {
      // Return a null if pipeline with the id not found
      if (e.responseCode == HTTP_NOT_FOUND) {
        log.debug("Existing pipeline with id {} not found. Returning null.", id)
        return null
      }
      throw e
    }
  }
}
