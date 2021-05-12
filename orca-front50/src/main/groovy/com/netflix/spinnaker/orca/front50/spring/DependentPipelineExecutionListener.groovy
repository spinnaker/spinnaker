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

package com.netflix.spinnaker.orca.front50.spring

import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.exceptions.PipelineTemplateValidationException
import com.netflix.spinnaker.orca.api.pipeline.ExecutionPreprocessor
import com.netflix.spinnaker.orca.front50.DependentPipelineStarter
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.listeners.ExecutionListener
import com.netflix.spinnaker.orca.listeners.Persister
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipelinetemplate.V2Util
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE

@Slf4j
@CompileDynamic
@Component
@ConditionalOnExpression('${front50.enabled:true}')
class DependentPipelineExecutionListener implements ExecutionListener {

  private final Front50Service front50Service
  private final DependentPipelineStarter dependentPipelineStarter
  private final FiatStatus fiatStatus
  private final List<ExecutionPreprocessor> executionPreprocessors

  private final ContextParameterProcessor contextParameterProcessor

  @Autowired
  DependentPipelineExecutionListener(Front50Service front50Service,
                                     DependentPipelineStarter dependentPipelineStarter,
                                     FiatStatus fiatStatus,
                                     Optional<List<ExecutionPreprocessor>> pipelinePreprocessors,
                                     ContextParameterProcessor contextParameterProcessor) {
    this.front50Service = front50Service
    this.dependentPipelineStarter = dependentPipelineStarter
    this.fiatStatus = fiatStatus
    this.executionPreprocessors = pipelinePreprocessors.orElse(null)
    this.contextParameterProcessor = contextParameterProcessor
  }

  @Override
  void afterExecution(Persister persister, PipelineExecution execution, ExecutionStatus executionStatus, boolean wasSuccessful) {
    if (!execution || !(execution.type == PIPELINE)) {
      return
    }

    def status = convertStatus(execution)
    def allPipelines = AuthenticatedRequest.allowAnonymous({front50Service.getAllPipelines()})
    if (executionPreprocessors) {
      // Resolve templated pipelines if enabled.
      allPipelines = allPipelines.collect { pipeline ->
       if (V2Util.isV2Pipeline(pipeline)) {
         try {
           return V2Util.planPipeline(contextParameterProcessor, executionPreprocessors, pipeline)
         } catch (PipelineTemplateValidationException ignored) {
           // Really no point in logging this error out here - the user created a bad template, which has no relation
           // to the currently executing pipeline
           return null
         } catch (Exception e) {
           log.error("Failed to plan V2 templated pipeline {}", pipeline.getOrDefault("id", "<UNKNOWN ID>"), e)
           return null
         }
       } else {
         return pipeline
       }
      }
    }

    allPipelines.findAll { (it != null) && (!it.disabled) }
      .each {
      it.triggers.each { trigger ->
        try {
          if (trigger.enabled &&
            trigger.type == 'pipeline' &&
            trigger.pipeline &&
            trigger.pipeline == execution.pipelineConfigId &&
            trigger.status.contains(status)
          ) {
            PipelineExecution.AuthenticationDetails authenticatedUser = null

            if (fiatStatus.enabled && trigger.runAsUser) {
              authenticatedUser = new PipelineExecution.AuthenticationDetails(trigger.runAsUser)
            }

            dependentPipelineStarter.trigger(
              it,
              execution.trigger?.user as String,
              execution,
              [:],
              null,
              authenticatedUser
            )
          }
        }
        catch (Exception e) {
          log.error(
            "Failed to process triggers for pipeline {} while triggering dependent pipelines",
            it.getOrDefault("id", "<UNKNOWN ID>"),
            e)
        }
      }
    }
  }

  private static String convertStatus(PipelineExecution execution) {
    switch (execution.status) {
      case ExecutionStatus.CANCELED:
        return 'canceled'
        break
      case ExecutionStatus.SUSPENDED:
        return 'suspended'
        break
      case ExecutionStatus.SUCCEEDED:
        return 'successful'
        break
      default:
        return 'failed'
    }
  }

  @Override
  int getOrder() {
    return HIGHEST_PRECEDENCE
  }
}
