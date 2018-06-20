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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.front50.DependentPipelineStarter
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.security.AuthenticatedRequest
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class StartPipelineTask implements Task {

  @Autowired(required = false)
  Front50Service front50Service

  @Autowired
  DependentPipelineStarter dependentPipelineStarter

  @Override
  TaskResult execute(Stage stage) {
    if (!front50Service) {
      throw new UnsupportedOperationException("Cannot start a stored pipeline, front50 is not enabled. Fix this by setting front50.enabled: true")
    }

    String application = stage.context.pipelineApplication ?: stage.context.application
    String pipelineId = stage.context.pipelineId ?: stage.context.pipeline
    Boolean isStrategy = stage.context.pipelineParameters?.strategy ?: false

    List<Map<String, Object>> pipelines = isStrategy ? front50Service.getStrategies(application) : front50Service.getPipelines(application, false)
    Map<String, Object> pipelineConfig = pipelines.find { it.id == pipelineId }

    if (!pipelineConfig) {
      throw new IllegalArgumentException("The referenced ${isStrategy ? 'custom strategy' : 'pipeline'} cannot be located (${pipelineId})")
    }

    if (pipelineConfig.getOrDefault("disabled", false)) {
      throw new IllegalArgumentException("The referenced ${isStrategy ? 'custom strategy' : 'pipeline'} is disabled")
    }

    def parameters = stage.context.pipelineParameters ?: [:]

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
      stage.context.user,
      stage.execution,
      parameters,
      stage.id,
      getUser(stage.execution)
    )

    new TaskResult(ExecutionStatus.SUCCEEDED, [executionId: pipeline.id, executionName: pipelineConfig.name])
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

    if (parentPipeline.authentication?.user) {
      return parentPipeline.authentication.toKorkUser().get()
    }

    return null
  }
}
