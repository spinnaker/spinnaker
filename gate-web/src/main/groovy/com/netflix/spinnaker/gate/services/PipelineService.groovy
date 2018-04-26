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


package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.security.RequestContext
import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.EchoService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
@Slf4j
class PipelineService {
  private static final String GROUP = "pipelines"

  @Autowired(required = false)
  Front50Service front50Service

  @Autowired(required = false)
  EchoService echoService

  @Autowired
  OrcaServiceSelector orcaServiceSelector

  @Autowired
  ApplicationService applicationService

  private final RetrySupport retrySupport = new RetrySupport()

  void deleteForApplication(String applicationName, String pipelineName) {
    front50Service.deletePipelineConfig(applicationName, pipelineName)
  }

  void save(Map pipeline) {
    HystrixFactory.newVoidCommand(GROUP, "savePipeline") {
      front50Service.savePipelineConfig(pipeline)
    } execute()
  }

  Map update(String pipelineId, Map pipeline) {
    HystrixFactory.newMapCommand(GROUP, "updatePipeline") {
      front50Service.updatePipeline(pipelineId, pipeline)
    } execute()
  }

  void move(Map moveCommand) { //TODO: use update endpoint when front50 is live
    front50Service.movePipelineConfig(moveCommand)
  }

  Map trigger(String application, String pipelineNameOrId, Map trigger) {
    def pipelineConfig = applicationService.getPipelineConfigForApplication(application, pipelineNameOrId)
    if (!pipelineConfig) {
      throw new NotFoundException("Pipeline configuration not found (id: ${pipelineNameOrId})")
    }
    pipelineConfig.trigger = trigger
    if (trigger.notifications) {
      if (pipelineConfig.notifications) {
        pipelineConfig.notifications = (List) pipelineConfig.notifications + (List) trigger.notifications
      } else {
        pipelineConfig.notifications = trigger.notifications
      }
    }
    if (pipelineConfig.parameterConfig) {
      Map triggerParams = (Map) trigger.parameters ?: [:]
      pipelineConfig.parameterConfig.each { Map paramConfig ->
        String paramName = paramConfig.name
        if (paramConfig.required && paramConfig.default == null) {
          if (triggerParams[paramName] == null) {
            throw new IllegalArgumentException("Required parameter ${paramName} is missing")
          }
        }
      }
    }
    orcaServiceSelector.withContext(RequestContext.get()).startPipeline(pipelineConfig, trigger.user?.toString())
  }

  Map startPipeline(Map pipelineConfig, String user) {
    orcaServiceSelector.withContext(RequestContext.get()).startPipeline(pipelineConfig, user)
  }

  Map getPipeline(String id) {
    orcaServiceSelector.withContext(RequestContext.get()).getPipeline(id)
  }

  List<Map> getPipelineLogs(String id) {
    orcaServiceSelector.withContext(RequestContext.get()).getPipelineLogs(id)
  }

  Map cancelPipeline(String id, String reason, boolean force) {
    setApplicationForExecution(id)
    orcaServiceSelector.withContext(RequestContext.get()).cancelPipeline(id, reason, force, "")
  }

  Map pausePipeline(String id) {
    setApplicationForExecution(id)
    orcaServiceSelector.withContext(RequestContext.get()).pausePipeline(id, "")
  }

  Map resumePipeline(String id) {
    setApplicationForExecution(id)
    orcaServiceSelector.withContext(RequestContext.get()).resumePipeline(id, "")
  }

  Map deletePipeline(String id) {
    setApplicationForExecution(id)
    orcaServiceSelector.withContext(RequestContext.get()).deletePipeline(id)
  }

  Map updatePipelineStage(String executionId, String stageId, Map context) {
    setApplicationForExecution(executionId)
    orcaServiceSelector.withContext(RequestContext.get()).updatePipelineStage(executionId, stageId, context)
  }

  Map restartPipelineStage(String executionId, String stageId, Map context) {
    setApplicationForExecution(executionId)
    orcaServiceSelector.withContext(RequestContext.get()).restartPipelineStage(executionId, stageId, context)
  }

  Map evaluateExpressionForExecution(String executionId, String pipelineExpression) {
    orcaServiceSelector.withContext(RequestContext.get()).evaluateExpressionForExecution(executionId, pipelineExpression)
  }

  /**
   * Retrieve an orca execution by id to populate RequestContext application
   *
   * @param id
   */
  void setApplicationForExecution(String id) {
    try {
      Map execution = retrySupport.retry({ -> getPipeline(id)}, 5, 1000, false)
      if (execution.containsKey("application")) {
        RequestContext.setApplication(execution.get("application").toString())
      }
    } catch (Exception e) {
      log.error("Error loading execution {} from orca", id, e)
    }
  }
}
