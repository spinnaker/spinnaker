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

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.EchoService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.OrcaService
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

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
  OrcaService orcaService

  @Autowired
  ApplicationService applicationService

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
      throw new PipelineConfigNotFoundException()
    }
    pipelineConfig.trigger = trigger
    if (trigger.notifications) {
      if (pipelineConfig.notifications) {
        pipelineConfig.notifications = (List) pipelineConfig.notifications + (List) trigger.notifications
      } else {
        pipelineConfig.notifications = trigger.notifications;
      }
    }
    orcaService.startPipeline(pipelineConfig, trigger.user?.toString())
  }

  Map startPipeline(Map pipelineConfig, String user) {
    orcaService.startPipeline(pipelineConfig, user)
  }

  Map getPipeline(String id) {
    orcaService.getPipeline(id)
  }

  Map cancelPipeline(String id, String reason, boolean force) {
    orcaService.cancelPipeline(id, reason, force, "")
  }

  Map pausePipeline(String id) {
    orcaService.pausePipeline(id, "")
  }

  Map resumePipeline(String id) {
    orcaService.resumePipeline(id, "")
  }

  Map deletePipeline(String id) {
    orcaService.deletePipeline(id)
  }

  Map updatePipelineStage(String executionId, String stageId, Map context) {
    orcaService.updatePipelineStage(executionId, stageId, context)
  }

  Map restartPipelineStage(String executionId, String stageId, Map context) {
    orcaService.restartPipelineStage(executionId, stageId, context)
  }

  @InheritConstructors
  @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "pipeline config not found!")
  static class PipelineConfigNotFoundException extends RuntimeException {}
}
